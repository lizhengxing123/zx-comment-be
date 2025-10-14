package com.lzx.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 的分布式 ID 生成器
 */
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class RedisIdWorker {

    private final StringRedisTemplate stringRedisTemplate;

    // 初始时间戳：2000-01-01 00:00:00 的秒数
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    // 序列号位数
    private static final int COUNT_BITS = 32;
    // 步长
    private static final long STEP = 1L;

    /**
     * 基于 Redis 的分布式 ID 生成器
     *
     * @param keyPrefix 键前缀，用于区分不同的 ID 类型，例如："order:"、"user:" 等
     * @return 分布式 ID
     */
    public long nextId(String keyPrefix) {
        // 1、生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        // 2、拼接键
        // 2.1、获取当前日期，格式：yyyy:MM:dd，精确到天，方便统计每天的 ID 数量
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2、拼接键，格式："icr:order:2025:01:01"
        String key = RedisConstants.ID_WORKER_KEY + keyPrefix + date;
        // 2.3、自增长
        long count = stringRedisTemplate.opsForValue().increment(key, STEP);
        // 生成 ID
        return timestamp << COUNT_BITS | count;
    }
}
