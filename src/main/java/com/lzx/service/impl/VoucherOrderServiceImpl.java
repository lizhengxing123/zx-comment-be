package com.lzx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzx.entity.SeckillVoucher;
import com.lzx.entity.VoucherOrder;
import com.lzx.exception.BaseException;
import com.lzx.mapper.SeckillVoucherMapper;
import com.lzx.mapper.VoucherOrderMapper;
import com.lzx.redis.RedisConstants;
import com.lzx.redis.RedisIdWorker;
import com.lzx.redis.SimpleRedisLock;
import com.lzx.service.VoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzx.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static javax.swing.Spring.minus;

/**
 * 优惠券订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class VoucherOrderServiceImpl implements VoucherOrderService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    //    private final RedissonClient redissonClient;
    // 注入自身代理对象，用于在订单处理线程中调用
    private VoucherOrderService proxy;

    // 秒杀券判断的 Lua 脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    // 秒杀券判断的 Lua 脚本，使用 stream 队列
    private static final DefaultRedisScript<Long> SECKILL_STREAM_SCRIPT;

    static {
        // 从文件中加载 Lua 脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("scripts/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
        // 从文件中加载 Lua 脚本，使用 stream 队列
        SECKILL_STREAM_SCRIPT = new DefaultRedisScript<>();
        SECKILL_STREAM_SCRIPT.setLocation(new ClassPathResource("scripts/seckill-stream.lua"));
        SECKILL_STREAM_SCRIPT.setResultType(Long.class);
    }


    // 线程池，订单处理线程使用
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 在类初始化之后就应该立即执行订单处理线程
    @PostConstruct
    public void init() {
        // 启动订单处理线程
        // SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // Redis Stream 消息队列实现方案
    public class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1、从 Redis Stream 消息队列中获取订单信息，如果不存在则等待 2 s
                    // XREADGROUP GROUP g1 CONSUMER c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> voucherOrderRecords = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(RedisConstants.SECKILL_STREAMS_ORDER_KEY, ReadOffset.lastConsumed())
                    );
                    // 2、判断消息是否获取成功
                    if (voucherOrderRecords == null || voucherOrderRecords.isEmpty()) {
                        // 没有消息，继续下次循环，继续等待
                        continue;
                    }
                    // 3、消息获取成功，解析订单信息
                    MapRecord<String, Object, Object> record = voucherOrderRecords.get(0);
                    Map<Object, Object> map = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
                    log.info("处理订单：{}", voucherOrder.getId());
                    // 4、处理订单
                    handleVoucherOrder(voucherOrder);
                    // 5、ACK 确认消息处理完成
                    // XACK streams.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(
                            RedisConstants.SECKILL_STREAMS_ORDER_KEY,
                            "g1",
                            record.getId()
                    );
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }
    }

    /**
     * 处理 pending-list 中的订单
     */
    private void handlePendingList() {
        while (true) {
            try {
                // 1、从 pending list 中获取订单信息
                // XREADGROUP GROUP g1 CONSUMER c1 COUNT 1 STREAMS streams.order 0
                List<MapRecord<String, Object, Object>> voucherOrderRecords = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(RedisConstants.SECKILL_STREAMS_ORDER_KEY, ReadOffset.from("0"))
                );
                // 2、判断消息是否获取成功
                if (voucherOrderRecords == null || voucherOrderRecords.isEmpty()) {
                    // 没有消息，说明 pending-list 中没有消息，结束循环
                    break;
                }
                // 3、消息获取成功，解析订单信息
                MapRecord<String, Object, Object> record = voucherOrderRecords.get(0);
                Map<Object, Object> map = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
                log.info("处理 pending-list 订单：{}", voucherOrder.getId());
                // 4、处理订单
                handleVoucherOrder(voucherOrder);
                // 5、ACK 确认消息处理完成
                // XACK streams.order g1 id
                stringRedisTemplate.opsForStream().acknowledge(
                        RedisConstants.SECKILL_STREAMS_ORDER_KEY,
                        "g1",
                        record.getId()
                );
            } catch (Exception e) {
                log.error("处理 pending-list 订单异常", e);
                // 有异常之后，会继续下次循环，所以啥也不需要做
            }
        }
    }

    /* JVM BlockingQueue 阻塞队列实现方案
    // 阻塞队列
    private final BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<>(1024);
    // 订单处理任务，从阻塞队列中获取订单并处理
    public class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 从阻塞队列中获取订单，如果不存在则等待
                    VoucherOrder voucherOrder = voucherOrderQueue.take();
                    log.info("处理订单：{}", voucherOrder.getId());
                    // 处理订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单线程被中断", e);
                }
            }
        }
    }*/

    /**
     * 秒杀券下单
     *
     * @param voucherId 秒杀券 ID
     * @return 优惠券订单 ID
     */
    @Override
    @Transactional
    public Long seckillVoucher(Long voucherId) {
        // 悲观锁方案
        // return seckillVoucherWithPessimisticLock(voucherId);
        // 乐观锁方案
        // return seckillVoucherWithOptimisticLock(voucherId);
        // 处理秒杀券订单（包含一人一单校验、库存扣除和订单创建）使用 synchronized 实现
        // return seckillVoucherAndSinglePurchaseWithSynchronized(voucherId);
        // 处理秒杀券订单（包含一人一单校验、库存扣除和订单创建）使用 Redis 分布式锁实现
        // return seckillVoucherAndSinglePurchaseWithSimpleRedisLock(voucherId);
        // 处理秒杀券订单（包含一人一单校验、库存扣除和订单创建）使用 Redis 先判断，再使用阻塞队列实现
        // return seckillVoucherAndSinglePurchaseWithRedis(voucherId);
        // 处理秒杀券订单（包含一人一单校验、库存扣除和订单创建）使用 Redis Stream 队列
        return seckillVoucherAndSinglePurchaseWithRedisStream(voucherId);
    }

    // ------------------ 私有方法 -------------------

    /**
     * 秒杀券下单：悲观锁实现方案
     */
    public Long seckillVoucherWithPessimisticLock(Long voucherId) {
        // 使用 selectByIdForUpdate 悲观锁查询秒杀券详细信息
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectByIdForUpdate(voucherId);
        checkSeckillVoucher(seckillVoucher);

        // 扣减库存
        seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        seckillVoucherMapper.updateById(seckillVoucher);

        // 创建订单
        VoucherOrder voucherOrder = createVoucherOrder(voucherId);
        return voucherOrder.getId();
    }

    /**
     * 秒杀券下单：乐观锁实现方案
     */
    public Long seckillVoucherWithOptimisticLock(Long voucherId) {
        // 使用乐观锁查询秒杀券详细信息
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        checkSeckillVoucher(seckillVoucher);

        // 扣减库存，在扣减库存时需要判断库存和之前查到的一致，不一致则说明有其他线程先操作了库存
        // 扣减库存
        // MyBatis-Plus 默认的 set 方法是给字段赋一个固定值，
        // 不能直接做运算，所以这里要用 setSql() 写原生 SQL 片段。
        /*LambdaUpdateWrapper<SeckillVoucher> updateWrapper = Wrappers.lambdaUpdate(SeckillVoucher.class)
                .set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1)
                .eq(SeckillVoucher::getVoucherId, voucherId)
                // 相等会导致大量线程失败，稍微改进，库存大于0即可
                .gt(SeckillVoucher::getStock, 0);
        // 更新秒杀券库存
        int rows = seckillVoucherMapper.update(null, updateWrapper);*/

        // 更新库存
        updateStockWithOptimisticLock(voucherId);

        // 创建订单
        VoucherOrder voucherOrder = createVoucherOrder(voucherId);
        return voucherOrder.getId();
    }

    /**
     * 秒杀券下单：并实现一人一单
     * 使用 synchronized
     */
    public Long seckillVoucherAndSinglePurchaseWithSynchronized(Long voucherId) {
        // 使用乐观锁查询秒杀券详细信息
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        checkSeckillVoucher(seckillVoucher);

        // 对于一人一单需要加悲观锁，确保在扣减库存和创建订单之间不会有其他线程操作
        // 这个悲观锁可以使用用户ID，确保每个用户在购买时是独立的
        Long userId = UserHolder.getUser().getId();
        // 使用 intern 方法确保字符串在常量池中的唯一性
        // 外层 synchronized 锁是为了减少进入数据库的并发请求数，提高性能
        synchronized (userId.toString().intern()) {
            // 直接调用处理方法不会执行事务，因为事务是在代理类中实现的
            // 所以要先获取代理类
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            // 然后在代理类中执行事务，确保事务提交之后再释放锁
            return proxy.processSeckillVoucherOrder(voucherId);
        }
    }

    /**
     * 秒杀券下单：并实现一人一单
     * 使用自定义的 Redis 互斥锁实现
     */
    public Long seckillVoucherAndSinglePurchaseWithSimpleRedisLock(Long voucherId) {
        // 查询秒杀券详细信息
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        checkSeckillVoucher(seckillVoucher);

        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 使用 redisson 中的可重入锁实现
        // RLock lock = redissonClient.getLock(RedisConstants.REDISSON_LOCK_KEY + "order:" + userId);
        // 尝试获取锁
        boolean isLock = lock.tryLock(1200L);
        if (!isLock) {
            // 获取锁失败，说明有其他线程在处理该用户的订单，抛出错误
            throw new BaseException("不允许重复下单");
        }
        try {
            // 获取锁成功，说明当前线程是第一个处理该用户的订单，继续处理
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.processSeckillVoucherOrder(voucherId);
        } finally {
            // 最后释放锁，确保其他线程可以获取到锁
            lock.unlock();
        }
    }

    // 将一人一单校验、库存扣除和订单创建封装到一个方法中
    @Transactional(rollbackFor = Exception.class)
    public Long processSeckillVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 一人一单校验，悲观锁
        // 注意：这里必须用 FOR UPDATE 锁定记录
        // 必须在事务内使用 FOR UPDATE，否则锁不会生效
        Long count = voucherOrderMapper.countByUserIdAndVoucherId(userId, voucherId);
        // 检查是否已购买过该秒杀券
        if (count > 0) {
            throw new BaseException("用户已购买过该秒杀券");
        }

        // 更新库存
        updateStockWithOptimisticLock(voucherId);

        // 创建订单
        VoucherOrder voucherOrder = createVoucherOrder(voucherId);
        return voucherOrder.getId();
    }

    /**
     * 秒杀券下单：并实现一人一单
     * 使用 redis stream 队列
     */
    public Long seckillVoucherAndSinglePurchaseWithRedisStream(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order:");
        // 使用 Lua 脚本先进行判断
        Long result = stringRedisTemplate.execute(
                SECKILL_STREAM_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                Long.toString(orderId)
        );
        // 检查 Lua 脚本执行结果
        int status = result.intValue();
        if (status != 0) {
            throw new BaseException(status == 1 ? "秒杀券库存不足" : "用户已购买过该秒杀券");
        }
        // Lua 脚本执行成功，说明有购买资格
        // 对代理对象进行赋值，确保在事务中调用
        proxy = (VoucherOrderService) AopContext.currentProxy();
        // 返回 ID
        return orderId;

    }

    /**
     * 秒杀券下单：并实现一人一单
     * 使用 redis 先进行判断
     */
    public Long seckillVoucherAndSinglePurchaseWithRedis(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 使用 Lua 脚本先进行判断
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 检查 Lua 脚本执行结果
        int status = result.intValue();
        if (status != 0) {
            throw new BaseException(status == 1 ? "秒杀券库存不足" : "用户已购买过该秒杀券");
        }
        // Lua 脚本执行成功，说明有购买资格，保存到阻塞队列中
        Long orderId = redisIdWorker.nextId("order:");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        // voucherOrderQueue.add(voucherOrder);
        // 对代理对象进行赋值，确保在事务中调用
        proxy = (VoucherOrderService) AopContext.currentProxy();
        // 返回 ID
        return orderId;
    }

    /**
     * 处理秒杀券订单
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        log.info("handleVoucherOrder-处理订单：{}", voucherOrder.getId());
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 尝试获取锁
        boolean isLock = lock.tryLock(1200L);
        if (!isLock) {
            // 获取锁失败，说明有其他线程在处理该用户的订单，抛出错误
            log.error("用户 {} 已购买过该秒杀券，无法重复购买", userId);
            return;
        }
        try {
            // 获取锁成功，说明当前线程是第一个处理该用户的订单，继续处理
            log.info("handleVoucherOrder-获取锁成功，继续处理订单：{}", voucherOrder.getId());
            proxy.processSeckillVoucherOrder(voucherOrder);
        } finally {
            // 最后释放锁，确保其他线程可以获取到锁
            log.info("handleVoucherOrder-释放锁，订单：{}", voucherOrder.getId());
            lock.unlock();
        }
    }

    // 将一人一单校验、库存扣除和订单创建封装到一个方法中
    @Transactional(rollbackFor = Exception.class)
    public void processSeckillVoucherOrder(VoucherOrder voucherOrder) {
        log.info("processSeckillVoucherOrder-处理订单：{}", voucherOrder.getId());
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 一人一单校验，悲观锁
        // 注意：这里必须用 FOR UPDATE 锁定记录
        // 必须在事务内使用 FOR UPDATE，否则锁不会生效
        Long count = voucherOrderMapper.countByUserIdAndVoucherId(userId, voucherId);
        // 检查是否已购买过该秒杀券
        if (count > 0) {
            log.error("用户 {} 已购买过该秒杀券 {}", userId, voucherId);
//            throw new BaseException("用户已购买过该秒杀券");
        }

        // 更新库存
        updateStockWithOptimisticLock(voucherId);

        // 创建订单
        voucherOrderMapper.insert(voucherOrder);
    }

    /**
     * 对优惠券详细信息进行判断
     */
    private void checkSeckillVoucher(SeckillVoucher seckillVoucher) {
        // 检查秒杀券是否存在
        if (seckillVoucher == null) {
            throw new BaseException("秒杀券不存在");
        }
        // 检查秒杀券是否已开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new BaseException("秒杀券未开始");
        }
        // 检查秒杀券是否已过期
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BaseException("秒杀券已过期");
        }
        // 检查秒杀券是否已售罄
        if (seckillVoucher.getStock() <= 0) {
            throw new BaseException("秒杀券库存不足");
        }
    }

    /**
     * 创建订单
     */
    private VoucherOrder createVoucherOrder(Long voucherId) {
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrderMapper.insert(voucherOrder);
        return voucherOrder;
    }

    /**
     * 使用乐观锁更新库存
     */
    private void updateStockWithOptimisticLock(Long voucherId) {
        int rows = seckillVoucherMapper.update(
                null,
                Wrappers.<SeckillVoucher>lambdaUpdate()
                        // MyBatis-Plus 默认的 set 方法是给字段赋一个固定值，不能直接做运算，所以这里要用 setSql() 写原生 SQL 片段。
                        .setSql("stock = stock - 1") // 重要：直接在SQL中做减1
                        .eq(SeckillVoucher::getVoucherId, voucherId)
                        .gt(SeckillVoucher::getStock, 0) // 保证库存大于0
        );

        // 如果更新失败，则说明有其他线程先操作了库存，库存已被修改，需要抛出异常
        if (rows == 0) {
            log.error("秒杀券 {} 库存不足", voucherId);
//            throw new BaseException("秒杀券库存不足");
        }
    }
}
