package com.lzx.service;

import com.lzx.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 商户服务类
 */
public interface ShopService {

    /**
     * 根据 ID 查询商户详情
     *
     * @param id 商户 ID
     * @return 商户实体类
     */
    Shop getById(Long id);

    /**
     * 根据 ID 更新商户信息
     *
     * @param shop 商户实体类
     */
    void updateById(Shop shop);

    /**
     * 根据类型查询商户列表
     *
     * @param typeId  商户类型 ID
     * @param current 当前页码，默认值为 1
     * @param x       经度
     * @param y       纬度
     * @return 商户实体类列表
     */
    List<Shop> queryShopsByType(Integer typeId, Integer current, Double x, Double y);
}
