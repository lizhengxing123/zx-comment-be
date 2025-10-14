package com.lzx.service.impl;

import com.lzx.entity.SeckillVoucher;
import com.lzx.entity.Voucher;
import com.lzx.mapper.SeckillVoucherMapper;
import com.lzx.mapper.VoucherMapper;
import com.lzx.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
        // 3、返回秒杀券 ID
        return seckillVoucher.getVoucherId();
    }

}
