package com.lzx.service.impl;

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
import lombok.RequiredArgsConstructor;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static javax.swing.Spring.minus;

/**
 * 优惠券订单服务实现类
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class VoucherOrderServiceImpl implements VoucherOrderService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
//    private final RedissonClient redissonClient;


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
         return seckillVoucherAndSinglePurchaseWithSimpleRedisLock(voucherId);
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
            throw new BaseException("秒杀券库存不足");
        }
    }
}
