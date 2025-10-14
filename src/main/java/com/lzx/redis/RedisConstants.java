package com.lzx.redis;

public class RedisConstants {
    // 分布式 ID 生成器，键名前缀
    public static final String ID_WORKER_KEY = "icr:";
    // 登录验证码，键名前缀
    public static final String LOGIN_CODE_KEY = "login:code:";
    // 登录验证码，过期时间
    public static final Long LOGIN_CODE_TTL = 5L;
    // 登录用户，键名前缀
    public static final String LOGIN_USER_KEY = "login:token:";
    // 登录用户，过期时间
    public static final Long LOGIN_USER_TTL = 30L;
    // 空值缓存，过期时间
    public static final Long CACHE_NULL_TTL = 2L;
    // 店铺类型缓存，键名前缀
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:list";
    // 店铺类型缓存，过期时间，这个不是经常变化的，时间可以设置长一点，比如 1 天
    public static final Long CACHE_SHOP_TYPE_TTL = 1L;
    // 商户缓存，键名前缀
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    // 商户缓存，过期时间
    public static final Long CACHE_SHOP_TTL = 30L;
    // 互斥锁，键名前缀
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    // 互斥锁，过期时间
    public static final Long LOCK_SHOP_TTL = 10L;
    // 互斥锁，前缀
    public static final String LOCK_KEY_PREFIX = "lock:";
    // 互斥锁，过期时间
    public static final Long LOCK_KEY_TTL = 10L;

    // 秒杀商品库存缓存，键名前缀
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
