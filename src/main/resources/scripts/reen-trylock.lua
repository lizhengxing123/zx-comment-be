-- 可重入锁
-- 获取锁脚本
local key = KEYS[1]         -- 锁的key
local threadId = ARGV[1]    -- 线程唯一标识
local releaseTime = ARGV[2] -- 锁的自动释放时间

-- 判断锁是否存在
if (redis.call('exists', key) == 0) then
    -- 锁不存在，获取锁
    redis.call('hset', key, threadId, '1')
    -- 设置锁的自动释放时间
    redis.call('expire', key, releaseTime)
    -- 返回结果，1表示获取锁成功
    return 1
end

-- 锁存在，判断是否为当前线程
if (redis.call('hexists', key, threadId) == 1) then
    -- 锁存在且为当前线程，重入次数加1
    redis.call('hincrby', key, threadId, '1')
    -- 设置锁的自动释放时间
    redis.call('expire', key, releaseTime)
    -- 返回结果，1表示获取锁成功
    return 1
end

-- 锁存在但不为当前线程，返回结果，0表示获取锁失败
return 0
