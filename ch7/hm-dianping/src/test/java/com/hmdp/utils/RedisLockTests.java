package com.hmdp.utils;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/4 13:57
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@Log4j2
@SpringBootTest
public class RedisLockTests {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedissonClient redissonClient2;
    @Resource
    private RedissonClient redissonClient3;

    /**
     * 测试 Redis 锁是否可以再入
     */
    @Test
    public void test() throws InterruptedException {
        // 创建 Redisson 联锁
        String lockName = "lock:test";
        RLock lock1 = redissonClient.getLock(lockName);
        RLock lock2 = redissonClient2.getLock(lockName);
        RLock lock3 = redissonClient3.getLock(lockName);
        RLock lock = redissonClient.getMultiLock(lock1, lock2, lock3);
        boolean tryLock = lock.tryLock(10, TimeUnit.SECONDS);
        if (!tryLock) {
            log.error("test获取锁失败");
            return;
        }
        try {
            log.info("test获取锁成功");
            log.info("test执行业务代码");
            test2(lock);
        } finally {
            lock.unlock();
            log.info("test释放锁");
        }
    }

    private void test2(RLock lock) throws InterruptedException {
        boolean tryLock = lock.tryLock(10, TimeUnit.SECONDS);
        if (!tryLock) {
            log.error("test2获取锁失败");
            return;
        }
        try {
            log.info("test2获取锁成功");
            log.info("test2执行业务代码");
        } finally {
            lock.unlock();
            log.info("test2释放锁");
        }
    }
}
