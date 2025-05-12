package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/12 11:27
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@Log4j2
@SpringBootTest
public class RedisUVTests {
    private final RedisUV redisUV;

    public RedisUVTests(@Autowired StringRedisTemplate stringRedisTemplate) {
        redisUV = new RedisUV(stringRedisTemplate);
    }

    @Test
    public void testRecordUserCall() {
        // 模拟 10000 次随机用户请求
        final int COUNT = 10000;
        for (int i = 0; i < COUNT; i++) {
            // 随机用户id
            int userId = RandomUtil.randomInt(1, 100);
            redisUV.recordUserCall(userId);
        }
    }

    @Test
    public void testDateUV() {
        // 测试获取日uv
        LocalDateTime now = LocalDateTime.now();
        // 获取最近 N 天的 UV
        final int DAYS = 10;
        for (int i = 0; i < DAYS; i++) {
            long uv = redisUV.getUV(now.toLocalDate());
            log.info(uv);
            now = now.minusDays(1);
        }
    }

    @Test
    void testMonthUV() {
        // 测试获取月uv
        LocalDateTime now = LocalDateTime.now();
        long uv = redisUV.getUV(YearMonth.of(now.getYear(), now.getMonth()));
        log.info(uv);
    }
}
