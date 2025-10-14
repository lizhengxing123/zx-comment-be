package com.lzx.redis;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
     * 缓存穿透解决方案：缓存空对象
     * 缓存穿透：查询不存在的商户 ID，由于缓存中没有该商户，每次都要查询数据库，数据库中也不存在，也不会建立缓存，就会导致一直请求数据库。
     * 解决方法：
     * 1. 缓存空对象：当查询到数据库中不存在该商户时，缓存一个空对象，设置较短的过期时间，避免缓存穿透。
     * 2. 布隆过滤器：在查询数据库之前，先使用布隆过滤器判断该商户是否存在，不存在则直接返回，避免查询数据库。
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
     * 缓存击穿解决方案：逻辑过期
     * 缓存击穿：也叫热点 Key 问题，就是一个被高并发访问并且缓存重建业务较复杂的 Key 突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击。
     * 解决方法：
     * 1. 互斥锁：如果查询缓存未命中，先尝试获取一个互斥锁，只有获取到锁的线程才可以查询数据库，重建缓存数据，其他线程等待。
     * 2. 逻辑过期：在缓存中存储商户信息时，额外存储一个过期时间，当查询到过期时间时，先判断是否过期，如果过期，则新开一个线程异步查询数据库，
     * 更新缓存数据，并返回之前的缓存数据，等到新线程查询完成后，新一轮的请求会返回新的缓存数据。
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
     * 缓存击穿解决方案：互斥锁
     * 缓存击穿：也叫热点 Key 问题，就是一个被高并发访问并且缓存重建业务较复杂的 Key 突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击。
     * 解决方法：
     * 1. 互斥锁：如果查询缓存未命中，先尝试获取一个互斥锁，只有获取到锁的线程才可以查询数据库，重建缓存数据，其他线程等待。
     * 2. 逻辑过期：在缓存中存储商户信息时，额外存储一个过期时间，当查询到过期时间时，先判断是否过期，如果过期，则新开一个线程异步查询数据库，
     * 更新缓存数据，并返回之前的缓存数据，等到新线程查询完成后，新一轮的请求会返回新的缓存数据。
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
    public <T, ID> T queryWithMutex(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> dbFallback, long timeout, TimeUnit unit, String lockKeyPrefix) {
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