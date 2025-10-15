package com.lzx.service.impl;

import com.lzx.entity.SeckillVoucher;
import com.lzx.entity.Voucher;
import com.lzx.mapper.SeckillVoucherMapper;
import com.lzx.mapper.VoucherMapper;
import com.lzx.redis.RedisConstants;
import com.lzx.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠券服务实现类
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class VoucherServiceImpl implements VoucherService {

    private final VoucherMapper voucherMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final StringRedisTemplate redisTemplate;


    /**
     * 新增秒杀券
     *
     * @param voucher 优惠券信息：包含秒杀券相关信息
     * @return 秒杀券 ID
     */
    @Override
    @Transactional
    public Long addSeckillVoucher(Voucher voucher) {
        // 1、保存优惠券信息
        voucherMapper.insert(voucher);
        // 2、保存秒杀券信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherMapper.insert(seckillVoucher);
        // 同时将库存保存到Redis中
        Long voucherId = seckillVoucher.getVoucherId();
        String redisKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        redisTemplate.opsForValue().set(redisKey, seckillVoucher.getStock().toString());
        // 3、返回秒杀券 ID
        return voucherId;
    }

}
