package com.lzx.service.impl;

import cn.hutool.json.JSONUtil;
import com.lzx.constant.RedisConstants;
import com.lzx.entity.ShopType;
import com.lzx.exception.BaseException;
import com.lzx.mapper.ShopTypeMapper;
import com.lzx.service.ShopTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 购物类型服务实现类
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ShopTypeServiceImpl implements ShopTypeService {

    private final ShopTypeMapper shopTypeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 获取所有店铺类型列表
     *
     * @return 所有店铺类型实体列表
     */
    @Override
    public List<ShopType> getList() {
        // 构建 Redis 缓存键名和过期时间
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        Long ttl = RedisConstants.CACHE_SHOP_TYPE_TTL;

        // 1. 从 Redis 中查询店铺类型缓存
        List<ShopType> shopTypeList = getShopTypes(key);
        if (!shopTypeList.isEmpty()) {
            // 2. 如果存在，直接返回缓存中的店铺类型
            return shopTypeList;
        }

        // 3. 如果不存在，从数据库中查询店铺类型
        shopTypeList = shopTypeMapper.selectList(null);
        if (shopTypeList.isEmpty()) {
            // 4. 如果数据库中也不存在，抛出异常
            throw new BaseException("店铺类型不存在");
        }

        // 5. 如果数据库中存在，将店铺类型缓存到 Redis 中，使用 List 类型存储
        addShopTypes(key, ttl, shopTypeList);

        // 6. 返回店铺类型
        return shopTypeList;
    }

    // --------------------- 私有方法 ---------------------

    /**
     * 添加店铺类型列表到 Redis 中
     *
     * @param key          缓存键名
     * @param ttl          缓存过期时间
     * @param shopTypeList 店铺类型实体列表
     */
    private void addShopTypes(String key, Long ttl, List<ShopType> shopTypeList) {
        List<String> jsonList = shopTypeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        stringRedisTemplate.opsForList().rightPushAll(key, jsonList);
        stringRedisTemplate.expire(key, ttl, TimeUnit.DAYS);
    }

    /**
     * 从 Redis 中获取店铺类型列表
     *
     * @param key 缓存键名
     * @return 店铺类型实体列表
     */
    public List<ShopType> getShopTypes(String key) {
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return List.of();
        }

        return jsonList.stream()
                .map(json -> JSONUtil.toBean(json, ShopType.class))
                .collect(Collectors.toList());
    }
}
