package com.lzx.service;

import com.lzx.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券订单服务接口
 */
public interface VoucherOrderService {

    /**
     * 秒杀券下单
     *
     * @param id 秒杀券 ID
     * @return 优惠券订单 ID
     */
    Long seckillVoucher(Long id);

    /**
     * 处理秒杀券订单（包含一人一单校验、库存扣除和订单创建）
     *
     * @param voucherId 秒杀券 ID
     * @return 优惠券订单 ID
     */
    Long processSeckillVoucherOrder(Long voucherId);
}
