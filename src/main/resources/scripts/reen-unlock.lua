-- 可重入锁
-- 释放锁
local key = KEYS[1]         -- 锁的key
local threadId = ARGV[1]    -- 线程唯一标识
local releaseTime = ARGV[2] -- 锁的自动释放时间

-- 判断锁是否还被自己持有
if (redis.call('hexists', key, threadId) == 0) then
    -- 如果不是自己，直接返回
    return nil
end

-- 锁存在且为当前线程，重入次数减1
local count = redis.call('hincrby', key, threadId, '-1')
-- 判断减1后的重入次数是否为0
if (count > 0) then
    -- 如果重入次数大于0，说明不能释放锁，重置有效期后返回
    redis.call('expire', key, releaseTime)
    return nil
else
    -- 如果重入次数为0，说明可以释放锁，直接删除锁
    redis.call('del', key)
    return nil
end
