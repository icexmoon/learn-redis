package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2024/2/22 15:02
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 全局唯一ID生成器（基于 Redis）
 */
@Component
public class GlobalIdGenerator {
    private static final long BASE_TIMESTAMP = 1704067200L;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成全局唯一ID
     *
     * @param key 业务代码
     * @return 全局唯一ID
     */
    public long genGlobalId(String key) {
        // 计算时间戳差额
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BASE_TIMESTAMP;
        // 从 Redis 获取对应的自增量
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "incr:" + key + ":" + dateStr;
        long incrNum = stringRedisTemplate.opsForValue().increment(redisKey);
        // 生成唯一id
        return timestamp << 32 | incrNum;
    }

    public static void main(String[] args) {
        // 计算时间戳基准时间戳
        long baseTimestamp = LocalDateTime.of(2024, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        System.out.println(baseTimestamp);
    }
}
