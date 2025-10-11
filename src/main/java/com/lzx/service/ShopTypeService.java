package com.lzx.service;

import com.lzx.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 购物类型服务类
 */
public interface ShopTypeService {

    /**
     * 获取所有购物类型列表
     *
     * @return 所有购物类型实体列表
     */
    List<ShopType> getList();
}
