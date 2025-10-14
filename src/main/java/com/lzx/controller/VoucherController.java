package com.lzx.controller;

import com.lzx.entity.Voucher;
import com.lzx.result.Result;
import com.lzx.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券接口
 */
@Slf4j
@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class VoucherController {

    private final VoucherService voucherService;

    /**
     * 新增秒杀券
     *
     * @param voucher 优惠券信息：包含秒杀券相关信息
     * @return 秒杀券 ID
     */
    @PostMapping("/seckill")
    public Result<Long> addSeckillVoucher(@RequestBody Voucher voucher) {
        Long id = voucherService.addSeckillVoucher(voucher);
        return Result.success("新增秒杀券成功", id);
    }
}
