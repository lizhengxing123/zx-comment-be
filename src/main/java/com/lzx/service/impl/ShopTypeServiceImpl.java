package com.lzx.service.impl;

import com.lzx.entity.ShopType;
import com.lzx.mapper.ShopTypeMapper;
import com.lzx.service.ShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物类型服务实现类
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ShopTypeServiceImpl implements ShopTypeService {

    private final ShopTypeMapper shopTypeMapper;

    /**
     * 获取所有购物类型列表
     *
     * @return 所有购物类型实体列表
     */
    @Override
    public List<ShopType> getList() {
        return shopTypeMapper.selectList(null);
    }
}
