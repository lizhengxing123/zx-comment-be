package com.lzx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lzx.constant.MessageConstants;
import com.lzx.constant.RedisConstants;
import com.lzx.entity.Shop;
import com.lzx.exception.BaseException;
import com.lzx.mapper.ShopMapper;
import com.lzx.result.CacheResult;
import com.lzx.service.ShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzx.utils.CacheClient;
import com.lzx.utils.RedisData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 商户服务实现类
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    // 线程池：用于异步更新缓存
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 根据 ID 查询商户详情
     *
     * @param id 商户 ID
     * @return 商户实体类
     */
    @Override
    public Shop getById(Long id) {
        // 解决缓存穿透问题
        // Shop shop = getShopWithPassThrough(id);
        /*Shop shop = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                shopMapper::selectById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );*/
        // 互斥锁解决缓存击穿问题
        // Shop shop = getShopWithMutex(id);
        /*Shop shop = cacheClient.queryWithMutex(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                shopMapper::selectById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );*/
        // 逻辑过期解决缓存击穿问题
        // Shop shop = getShopWithLogicalExpiration(id);
        Shop shop = cacheClient.queryWithLogicalExpiration(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                shopMapper::selectById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );

        if (shop == null) {
            // 抛出异常
            throw new BaseException(MessageConstants.SHOP_NOT_FOUND);
        }
        return shop;
    }

    /**
     * 根据 ID 更新商户信息
     *
     * @param shop 商户实体类
     */
    @Override
    @Transactional
    public void updateById(Shop shop) {
        // 1. 更新数据库中的商户信息
        int rows = shopMapper.updateById(shop);
        if (rows == 0) {
            // 2. 如果更新失败，抛出异常
            throw new BaseException("更新商户信息失败");
        }

        // 3. 删除 Redis 中的商户缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }

    // --------------------- 私有方法 ---------------------------

    /**
     * 缓存穿透解决方案：缓存空对象
     * 缓存穿透：查询不存在的商户 ID，由于缓存中没有该商户，每次都要查询数据库，数据库中也不存在，也不会建立缓存，就会导致一直请求数据库。
     * 解决方法：
     * 1. 缓存空对象：当查询到数据库中不存在该商户时，缓存一个空对象，设置较短的过期时间，避免缓存穿透。
     * 2. 布隆过滤器：在查询数据库之前，先使用布隆过滤器判断该商户是否存在，不存在则直接返回，避免查询数据库。
     *
     * @param id 商户 ID
     * @return 商户实体类
     */
    private Shop getShopWithPassThrough(Long id) {
        // 构建 Redis 缓存键名
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询商户缓存
        CacheResult<Shop> cacheResult = getShopFromCache(key, Shop.class);
        if (cacheResult.isExists()) {
            // 2. 如果存在，直接返回缓存中的商户
            return cacheResult.getData();
        }

        // 3. 如果不存在，从数据库中查询商户
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            // 4. 如果数据库中也不存在
            // 4.1 缓存空对象，设置过期时间，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 4.2 返回 null
            return null;
        }

        // 5. 如果数据库中存在，将商户缓存到 Redis 中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6. 返回商户
        return shop;
    }


    /**
     * 缓存击穿解决方案：互斥锁
     * 缓存击穿：也叫热点 Key 问题，就是一个被高并发访问并且缓存重建业务较复杂的 Key 突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击。
     * 解决方法：
     * 1. 互斥锁：如果查询缓存未命中，先尝试获取一个互斥锁，只有获取到锁的线程才可以查询数据库，重建缓存数据，其他线程等待。
     * 2. 逻辑过期：在缓存中存储商户信息时，额外存储一个过期时间，当查询到过期时间时，先判断是否过期，如果过期，则新开一个线程异步查询数据库，
     * 更新缓存数据，并返回之前的缓存数据，等到新线程查询完成后，新一轮的请求会返回新的缓存数据。
     *
     * @param id 商户 ID
     * @return 商户实体类
     */
    private Shop getShopWithMutex(Long id) {
        // 构建 Redis 缓存键名
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询商户缓存
        CacheResult<Shop> cacheResult = getShopFromCache(key, Shop.class);
        if (cacheResult.isExists()) {
            // 2. 如果存在，直接返回缓存中的商户
            return cacheResult.getData();
        }

        // 3. 缓存中不存在
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            // 3.1 尝试获取互斥锁
            boolean isLocked = tryLock(lockKey);
            if (!isLocked) {
                // 3.2 如果获取锁失败，等待一段时间后重试
                Thread.sleep(50);
                // 递归调用，重新尝试获取锁
                return getShopWithMutex(id); // 或用循环
            }

            // 4. 如果获取到锁，再次检查缓存是否存在
            cacheResult = getShopFromCache(key, Shop.class);
            if (cacheResult.isExists()) {
                // 4.1 如果存在，直接返回缓存中的商户
                return cacheResult.getData();
            }

            // 5. 实现缓存重建，从数据库中查询商户
            shop = shopMapper.selectById(id);
            if (shop == null) {
                // 5.1 如果数据库中也不存在，缓存空对象，设置过期时间，避免缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 5.2 返回 null
                return null;
            }

            // 5.3 如果数据库中存在，将商户缓存到 Redis 中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            // 6. 释放互斥锁
            unlock(lockKey);
        }

        // 7. 返回商户
        return shop;
    }

    /**
     * 缓存击穿解决方案：逻辑过期
     * 缓存击穿：也叫热点 Key 问题，就是一个被高并发访问并且缓存重建业务较复杂的 Key 突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击。
     * 解决方法：
     * 1. 互斥锁：如果查询缓存未命中，先尝试获取一个互斥锁，只有获取到锁的线程才可以查询数据库，重建缓存数据，其他线程等待。
     * 2. 逻辑过期：在缓存中存储商户信息时，额外存储一个过期时间，当查询到过期时间时，先判断是否过期，如果过期，则新开一个线程异步查询数据库，
     * 更新缓存数据，并返回之前的缓存数据，等到新线程查询完成后，新一轮的请求会返回新的缓存数据。
     *
     * @param id 商户 ID
     * @return 商户实体类
     */
    private Shop getShopWithLogicalExpiration(Long id) {
        // 构建 Redis 缓存键名
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1、从 Redis 中查询商户缓存
        Shop shop = checkCacheAndGetShop(key);
        if (shop != null) {
            // 缓存未过期，直接返回缓存中的商户
            return shop;
        }

        // 2、如果已过期，需要重建缓存
        // 2.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLocked = tryLock(lockKey);
        if (isLocked) {
            // 2.2 获取互斥锁成功，再次检查缓存是否存在
            shop = checkCacheAndGetShop(key);
            if (shop != null) {
                // 2.3 如果缓存未过期，直接返回缓存中的商户
                unlock(lockKey);
                return shop;
            }

            // 2.4 如果缓存已过期，开启独立线程进行缓存重建
            executorService.submit(() -> {
                try {
                    // 缓存重建
                    this.saveShop2Redis(id, 20L);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 3、返回过期的商户信息
        return getExpiredShopFromCache(key);
    }


    /**
     * 获取缓存中的数据
     *
     * @param key Redis 缓存键名
     * @return 缓存结果对象，包含是否存在和数据
     */
    private <T> CacheResult<T> getShopFromCache(String key, Class<T> clazz) {
        // 1. 从 Redis 中查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            // 2. 如果存在，直接返回缓存中的商户
            return CacheResult.hit(JSONUtil.toBean(shopJson, clazz));
        }
        // 判断是否为空字符串
        if (StrUtil.equals(shopJson, "")) {
            // 如果为空字符串，查询到的是穿透的结果，返回 null
            return CacheResult.nullValue();
        }

        // 3. 如果不存在，返回缓存未命中
        return CacheResult.miss();
    }

    /**
     * 检查缓存是否过期，并返回未过期的商户信息
     *
     * @param key Redis 缓存键名
     * @return 商户实体类
     */
    private Shop checkCacheAndGetShop(String key) {
        CacheResult<RedisData> cacheResult = getShopFromCache(key, RedisData.class);
        if (!cacheResult.isExists() || cacheResult.getData() == null) {
            // 缓存不存在，或者缓存数据为空，返回 null
            return null;
        }

        RedisData redisData = cacheResult.getData();
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop; // 未过期
        }

        return null; // 已过期
    }

    /**
     * 从缓存中获取过期的商户信息
     *
     * @param key Redis 缓存键名
     * @return 商户实体类
     */
    private Shop getExpiredShopFromCache(String key) {
        CacheResult<RedisData> cacheResult = getShopFromCache(key, RedisData.class);
        if (!cacheResult.isExists() || cacheResult.getData() == null) {
            return null;
        }

        RedisData redisData = cacheResult.getData();
        return JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    }


    /**
     * 尝试获取锁
     *
     * @param key 锁的键名
     * @return 是否成功获取锁
     */
    private Boolean tryLock(String key) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(success);
    }

    /**
     * 释放锁
     *
     * @param key 锁的键名
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 向 redis 中预先存入shop，设置逻辑过期时间
     *
     * @param id            商户 ID
     * @param expireSeconds 过期时间，单位：秒
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 构建 Redis 缓存键名
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 从数据库中查询商户
        Shop shop = shopMapper.selectById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 向 Redis 中存入商户信息
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

}
