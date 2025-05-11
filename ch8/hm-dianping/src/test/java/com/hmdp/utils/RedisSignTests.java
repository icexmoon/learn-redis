package com.hmdp.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/11 19:38
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@SpringBootTest
public class RedisSignTests {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisSign redisSign;

    public RedisSignTests(@Autowired StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        redisSign = new RedisSign(stringRedisTemplate);
    }

    @Test
    public void testSign() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime date = now;
        for (int i = 0; i < 90; i++) {
            redisSign.sign(111, date.toLocalDate());
            date = date.minusDays(1);
        }
    }

    @Test
    public void testIsSign() {
        LocalDateTime now = LocalDateTime.now();
        boolean signed = redisSign.isSigned(111, now.toLocalDate());
        Assertions.assertTrue(signed);
        signed = redisSign.isSigned(111, now.minusDays(1).toLocalDate());
        Assertions.assertFalse(signed);
    }

    @Test
    public void testSignRecord() {
        Map<LocalDate, Boolean> signRecord = redisSign.getSignRecord(111, YearMonth.now());
        System.out.println(signRecord);
    }

    @Test
    public void testContinueSignDays() {
        int continueSignDays = redisSign.getContinueSignDays(111);
        System.out.println(continueSignDays);
    }
}
