--[[
    @描述：Redis 锁释放（支持再入）
]] --
local key = KEYS[1] -- Redis 锁的对应的 key
local threadId = ARGV[1] -- 持有锁的线程标识
local timeoutSec = ARGV[2] -- 锁的自动过期时长（单位秒）
-- 检查锁是否已经存在
if (redis.call('exists', key) == 0) then
    -- 锁不存在，返回错误信息
    return 0
end
-- 锁存在，检查是否当前线程持有的锁
if (redis.call('HEXISTS', key, threadId) == 0) then
    -- 不是当前线程持有的锁，返回错误信息
    return 0
end
-- 是当前线程持有的锁，计数器-1
redis.call('HINCRBY', key, threadId, -1)
-- 如果计数器小于等于0，删除锁
if (tonumber(redis.call('HGET', key, threadId)) <= 0) then
    redis.call('del', key)
    return 1
end
-- 如果计数器还未归0，更新锁的有效时长
redis.call('expire', key, timeoutSec)
return 1
