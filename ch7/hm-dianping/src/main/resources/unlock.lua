-- Redis 锁删除脚本，对比 Redis 中的锁标识与当前给定标识，如果一致，删除，否则不删除
------------------
-- 从命令行获取参数
-- Redis 锁对应的key
local lockKey = KEYS[1]
-- Redis 锁需要匹配的目标标识
local targetKeyVal = ARGV[1]
-- 获取 Redis 中存储的锁的当前值
local keyVal = redis.call('get', lockKey)
if (targetKeyVal == keyVal) then
    -- 删除锁对应的key
    return redis.call('del', lockKey)
end
-- 失败返回
return 0
