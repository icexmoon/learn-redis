--[[
    @描述：在 Redis 中完成优惠券秒杀资格判断
    @参数：优惠券id，用户id
    @返回值：0 表示秒杀成功，1 表示库存不足，2 表示该用户已经拥有该优惠券
]] --    
local voucherId = ARGV[1] -- 优惠券id
local userId = ARGV[2] -- 用户id
local orderId = ARGV[3] -- 订单id
local stockKey = "seckill:stock:" .. voucherId -- 存储优惠券库存的key
local voucherUsersKey = "seckill:users:" .. voucherId -- 存储已经拥有该优惠券的用户id的key
-- 如果 redis 中不存在库存信息，视为不能秒杀
if (redis.call('EXISTS', stockKey) == 0) then
    return 1
end
-- 判断库存是否足够
local stock = tonumber(redis.call('GET', stockKey))
if (stock <= 0) then
    -- 库存不足
    return 1
end
-- 判断用户是否已经有该优惠券
if (redis.call('SISMEMBER', voucherUsersKey, userId) == 1) then
    -- 用户已经拥有该优惠券
    return 2
end
-- 库存扣减
redis.call('INCRBY', stockKey, -1)
-- 将用户信息记录到优惠券-用户集合
redis.call('SADD', voucherUsersKey, userId)
-- 发送消息到消息队列
redis.call('XADD', 'seckill:msg-queue', '*', 'voucherId', voucherId, 'userId', userId, 'id', orderId)
return 0
