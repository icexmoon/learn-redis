package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/3 15:05
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 基于 Redis 实现的分布式锁
 */
public class SimpleRedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    // 用于锁的业务名称
    private final String businessName;
    // 锁的统一前缀
    private static final String KEY_PREFIX = "lock:";
    // redis key
    private final String redisKey;
    // uuid，用于区分不同 JVM 创建的锁
    private static final String uuid = UUID.randomUUID().toString(true);
    // Redis 锁获取脚本
    private static final DefaultRedisScript<Long> LOCK_SCRIPT;

    static {
        LOCK_SCRIPT = new DefaultRedisScript<>();
        // 指定脚本的位置
        LOCK_SCRIPT.setLocation(new ClassPathResource("reentrant-lock.lua"));
        // 指定脚本的返回值类型
        LOCK_SCRIPT.setResultType(Long.class);
    }

    // Redis 锁释放脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 指定脚本的位置
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("reentrant-unlock.lua"));
        // 指定脚本的返回值类型
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String businessName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.businessName = businessName;
        this.redisKey = KEY_PREFIX + businessName;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        final String jvmThreadId = getJvmThreadId();
        Long res = stringRedisTemplate.execute(LOCK_SCRIPT,
                Collections.singletonList(redisKey),
                jvmThreadId,
                Long.toString(timeoutSec));
        return res != null && res > 0;
    }

    /**
     * 获取锁位于JVM当前线程的唯一标识
     *
     * @return 锁位于JVM当前线程的唯一标识
     */
    private String getJvmThreadId() {
        // 获取当前线程id，用于区分同一个 JVM 的不同线程创建的锁
        long threadId = Thread.currentThread().getId();
        // 拼接 uuid和线程id，可以唯一确定一个JVM中的一个线程
        final String jvmThreadId = uuid + "-" + threadId;
        return jvmThreadId;
    }

    @Override
    public void unlock(long timeoutSec) {
        // 使用 lua 脚本删除 Redis 锁
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(redisKey),
                getJvmThreadId(),
                Long.toString(timeoutSec));
    }

    @Override
    public void unlock() {
        unlock(200);
    }
}
