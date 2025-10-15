-- 秒杀券相关业务，使用 stream 队列
-- 1、参数列表
-- 1.1、优惠券 ID
local voucherId = ARGV[1]
-- 1.2、用户 ID
local userId = ARGV[2]
-- 1.3、订单 ID
local orderId = ARGV[3]

-- 2、数据key
-- 2.1、秒杀券库存 key，里面存的是秒杀券的库存数量
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2、秒杀券订单 key，里面存的是已购买秒杀券的用户 ID 集合
local orderKey = 'seckill:order:' .. voucherId

-- 3、业务逻辑
-- 3.1、判断秒杀券是否还有库存
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 秒杀券不存在或已售罄
    return 1
end
-- 3.2、判断用户是否已经购买过该秒杀券
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 用户已经购买过该秒杀券
    return 2
end
-- 3.3、秒杀券库存减一
redis.call('decr', stockKey)
-- 3.4、将用户 ID 加入已购买秒杀券的用户 ID 集合
redis.call('sadd', orderKey, userId)
-- 3.5、发送消息到 stream 队列
redis.call('xadd', 'streams.order', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
-- 3.6、返回 0 表示秒杀券购买成功
return 0
