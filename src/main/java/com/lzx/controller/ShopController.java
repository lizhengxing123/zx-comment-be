package com.lzx.controller;

import com.lzx.entity.Shop;
import com.lzx.result.Result;
import com.lzx.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

/**
 * 商户接口
 */
@Slf4j
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ShopController {

    private final ShopService shopService;

    /**
     * 根据 ID 查询商户详情
     *
     * @param id 商户 ID
     * @return 商户实体类
     */
    @GetMapping("/{id}")
    public Result<Shop> getShopById(@PathVariable("id") Long id) {
        Shop shop = shopService.getById(id);
        return Result.success("获取商户详情成功", shop);
    }

    /**
     * 根据 ID 更新商户信息
     *
     * @param shop 商户实体类
     * @return 更新结果
     */
    @PutMapping
    public Result<Void> updateShop(@RequestBody Shop shop) {
        shopService.updateById(shop);
        return Result.success("更新商户信息成功");
    }
}
