package com.lzx.config;

import org.springframework.context.annotation.Bean;

/*
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
*/
/**
 * Redisson 配置类
 */
//@Configuration
public class RedissonConfig {

    /**
     * 配置 Redisson 客户端
     */
    /*@Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 配置单节点 Redis 服务器，如果是集群模式，使用 useClusterServers() 方法
        config.useSingleServer()
                .setAddress("redis://192.168.2.28:6379")
                .setDatabase(5)
                .setPassword("AmberRedis");
        return Redisson.create(config);
    }*/
}
