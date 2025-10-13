package com.lzx.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lzx.constant.RedisConstants;
import com.lzx.entity.Shop;
import com.lzx.result.CacheResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 缓存客户端
 */
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 线程池：用于异步更新缓存
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);


    /**
     * 将任意类型的对象存储到缓存中，并设置过期时间
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    过期时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    /**
     * 将任意类型的对象存储到缓存中，并设置逻辑过期时间
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    过期时间单位
     */
    public void setWithLogicalExpire(String key, Object value, long timeout, TimeUnit unit) {
        // 设置数据
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        redisData.setData(value);
        // 存入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 从缓存中获取指定类型的对象，并处理缓存穿透
     *
     * @param keyPrefix  缓存键前缀
     * @param id         缓存键后缀（通常是业务主键）
     * @param clazz      缓存值类型
     * @param dbFallback 数据库查询函数，用于从数据库中查询对象
     * @param timeout    过期时间
     * @param unit       过期时间单位
     * @return 缓存值
     */
    public <T, ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        // 构建 Redis 缓存键名
        String key = keyPrefix + id;

        // 1. 从 Redis 中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            // 1.1 如果存在，直接返回
            return JSONUtil.toBean(json, clazz);
        }
        if (StrUtil.equals(json, "")) {
            // 1.2 如果为空字符串，说明是处理缓存穿透的空值，直接返回 null
            return null;
        }

        // 3. 如果不存在，从数据库中查询
        T data = dbFallback.apply(id);
        if (data == null) {
            // 4. 如果数据库中也不存在
            // 4.1 缓存空对象，设置过期时间，避免缓存穿透
            this.set(key, "", timeout, unit);
            // 4.2 返回 null
            return null;
        }

        // 5. 如果数据库中存在，将对象缓存到 Redis 中
        this.set(key, data, timeout, unit);

        // 6. 返回对象
        return data;
    }

    /**
     * 从缓存中获取指定类型的对象，并处理缓存击穿（逻辑过期）
     *
     * @param keyPrefix     缓存键前缀
     * @param id            缓存键后缀（通常是业务主键）
     * @param clazz         缓存值类型
     * @param dbFallback    数据库查询函数，用于从数据库中查询对象
     * @param timeout       过期时间
     * @param unit          过期时间单位
     * @param lockKeyPrefix 互斥锁键名前缀
     * @return 缓存值
     */
    public <T, ID> T queryWithLogicalExpiration(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> dbFallback, Long timeout, TimeUnit unit, String lockKeyPrefix) {
        // 构建 Redis 缓存键名
        String key = keyPrefix + id;
        // 1、从 Redis 中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 未命中缓存，返回 null
            return null;
        }

        // 2、命中缓存，判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 缓存未过期，直接返回缓存中的商户
            return data;
        }
        // 3、缓存过期，需要重建缓存
        // 3.1 获取互斥锁
        String lockKey = lockKeyPrefix + id;
        boolean isLocked = tryLock(lockKey);
        if (isLocked) {
            // 3.2 获取互斥锁成功，双重检查缓存
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                redisData = JSONUtil.toBean(json, RedisData.class);
                data = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
                if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                    unlock(lockKey);
                    return data;
                }
            }

            // 3.3 异步重建缓存
            executorService.submit(() -> {
                try {
                    T newData = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newData, timeout, unit);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 4、 返回过期的缓存信息
        return data;
    }

    /**
     * 从缓存中获取指定类型的对象，并处理缓存击穿（互斥锁）
     *
     * @param keyPrefix     缓存键前缀
     * @param id            缓存键后缀（通常是业务主键）
     * @param clazz         缓存值类型
     * @param dbFallback    数据库查询函数，用于从数据库中查询对象
     * @param timeout       过期时间
     * @param unit          过期时间单位
     * @param lockKeyPrefix 互斥锁键名前缀
     * @return 缓存值
     */
    public <T, ID> T queryWithMutex(
            String keyPrefix,
            ID id,
            Class<T> clazz,
            Function<ID, T> dbFallback,
            long timeout,
            TimeUnit unit,
            String lockKeyPrefix
    ) {
        // 构建 Redis 缓存键名
        String key = keyPrefix + id;

        // 1. 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存命中
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, clazz);
        }

        // 3. 空值缓存
        if (StrUtil.equals(json, "")) {
            return null;
        }

        // 4. 缓存未命中，尝试获取锁
        T data = null;
        String lockKey = lockKeyPrefix + id;
        try {
            boolean isLocked = tryLock(lockKey);
            if (!isLocked) {
                // 4.1 获取锁失败，等待后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, clazz, dbFallback, timeout, unit, lockKey);
            }

            // 5. 获取互斥锁成功，二次检查缓存
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, clazz);
            }
            if (StrUtil.equals(json, "")) {
                return null;
            }

            // 6. 数据库查询
            data = dbFallback.apply(id);
            if (data == null) {
                // 6.1 如果数据库中也不存在，缓存空对象，设置过期时间，避免缓存穿透
                this.set(key, "", timeout, unit);
                return null;
            }

            // 7. 写入缓存
            this.set(key, data, timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }

        return data;
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
}