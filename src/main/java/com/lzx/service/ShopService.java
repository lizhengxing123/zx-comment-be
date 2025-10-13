package com.lzx.service;

import com.lzx.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

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
}
