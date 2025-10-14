package com.lzx.redis;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


/**
 * 简单的 Redis 互斥锁实现
 */
public class SimpleRedisLock implements ILock {

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    // 线程标识前缀
    private static final String THREAD_ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    // 释放锁的 Lua 脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        // 从文件中加载 Lua 脚本
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("scripts/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 生成线程标识
        String threadId = THREAD_ID_PREFIX + Thread.currentThread().threadId();
        // 尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_KEY_PREFIX + name, threadId, RedisConstants.LOCK_KEY_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        // 使用 Lua 脚本释放锁
        unlockWithLua();
    }

    /**
     * 使用 Lua 脚本释放锁
     */
    private void unlockWithLua() {
        // 执行 Lua 脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(RedisConstants.LOCK_KEY_PREFIX + name),
                THREAD_ID_PREFIX + Thread.currentThread().threadId()
        );
    }

    /**
     * 基础释放锁
     */
    private void unlockWithBase() {
        // 生成线程标识
        String threadId = THREAD_ID_PREFIX + Thread.currentThread().threadId();
        // 获取当前线程标识
        String currentThreadId = stringRedisTemplate.opsForValue().get(RedisConstants.LOCK_KEY_PREFIX + name);
        // 判断是否为当前线程标识
        if (threadId.equals(currentThreadId)) {
            // 释放锁
            stringRedisTemplate.delete(RedisConstants.LOCK_KEY_PREFIX + name);
        }
    }

}
