package com.lzx.controller;

import com.lzx.result.Result;
import com.lzx.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

/**
 * 优惠券订单接口
 */
@Slf4j
@RestController
@RequestMapping("/voucherOrders")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class VoucherOrderController {

    private final VoucherOrderService voucherOrderService;

    /**
     * 秒杀券下单接口
     *
     * @param id 秒杀券 ID
     * @return 优惠券订单 ID
     */
    @PostMapping("/seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable Long id) {
        log.info("秒杀券下单接口，秒杀券 ID：{}", id);
        Long orderId = voucherOrderService.seckillVoucher(id);
        return Result.success("秒杀券下单成功", orderId);
    }

}
