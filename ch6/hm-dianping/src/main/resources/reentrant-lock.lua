--[[
    @描述: Redis 锁获取脚本（支持再入）
    @版本: 1.0.0
]] --    
local key = KEYS[1] -- Redis 锁的对应的 key
local threadId = ARGV[1] -- 持有锁的线程标识
local timeoutSec = ARGV[2] -- 锁的自动过期时长（单位秒）
-- 检查锁是否已经存在
local exists = redis.call('exists', key)
if (exists == 0) then
    -- 如果锁不存在，添加（正常获取到锁）
    redis.call('hset', key, threadId, 1)
    -- 更新锁的过期时间
    redis.call('expire', key, timeoutSec)
    return 1
end
-- 如果锁存在，检查是否当前线程的锁
if (redis.call('HEXISTS', key, threadId) == 0) then
    -- 如果不是当前线程的锁，返回错误信息（互斥，没有获取到锁）
    return 0
end
-- 是当前线程的锁（再入）
-- 计数器+1
redis.call('HINCRBY', key, threadId, 1)
-- 更新过期时长
redis.call('expire', key, timeoutSec)
return 1
