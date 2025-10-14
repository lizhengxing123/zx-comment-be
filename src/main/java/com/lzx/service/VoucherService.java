package com.lzx.service;

import com.lzx.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券服务接口
 */
public interface VoucherService {

    /**
     * 新增秒杀券
     *
     * @param voucher 优惠券信息：包含秒杀券相关信息
     * @return 秒杀券 ID
     */
    Long addSeckillVoucher(Voucher voucher);
}
