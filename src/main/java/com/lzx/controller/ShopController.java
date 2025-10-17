package com.lzx.controller;

import com.lzx.entity.Shop;
import com.lzx.result.Result;
import com.lzx.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

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
        log.info("根据 ID 查询商户详情，id：{}", id);
        Shop shop = shopService.getById(id);
        return Result.success("获取商户详情成功", shop);
    }

    /**
     * 更新商户信息
     *
     * @param shop 商户实体类
     * @return 更新结果
     */
    @PutMapping
    public Result<Void> updateShop(@RequestBody Shop shop) {
        log.info("更新商户信息，shop：{}", shop);
        shopService.updateById(shop);
        return Result.success("更新商户信息成功");
    }

    /**
     * 根据类型查询商户列表
     *
     * @param typeId  商户类型 ID
     * @param current 当前页码，默认值为 1
     * @param x       经度
     * @param y       纬度
     * @return 商户实体类列表
     */
    @GetMapping("/of/type/")
    public Result<List<Shop>> getShopByType(
            @RequestParam Integer typeId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y
    ) {
        log.info("根据类型查询商户列表，typeId：{}，current：{}，x：{}，y：{}", typeId, current, x, y);
        List<Shop> shopList = shopService.queryShopsByType(typeId, current, x, y);
        return Result.success("根据类型查询商户列表成功", shopList);
    }
}
