package com.hmdp.utils;

import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.UV_KEY;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/12 11:09
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 基于 Redis 的 HyperLogLog 实现的 UV 统计
 */
public class RedisUV {

    private final HyperLogLogOperations<String, String> opsForHyperLogLog;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisUV(StringRedisTemplate stringRedisTemplate) {
        opsForHyperLogLog = stringRedisTemplate.opsForHyperLogLog();
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 记录一次用户请求
     *
     * @param userId 用户id
     */
    public void recordUserCall(long userId) {
        // 获取当天记录 uv 的 HyperLogLog 的key
        final String KEY = getKey(LocalDateTime.now().toLocalDate());
        opsForHyperLogLog.add(KEY, Long.toString(userId));
    }

    /**
     * 获取指定日期对应的 HyperLogLog 的 key
     *
     * @param date 日期
     * @return key
     */
    private String getKey(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        return UV_KEY + dateStr;
    }

    /**
     * 获取指定日期的 uv
     *
     * @param date 日期
     * @return uv
     */
    public long getUV(LocalDate date) {
        final String KEY = getKey(date);
        // 不需要考虑染回值是 null 的情况，缺少 key 时自动返回 0
        return opsForHyperLogLog.size(KEY);
    }

    /**
     * 统计指定月份的 uv 之和
     *
     * @param yearMonth 指定月份
     * @return uv 之和
     */
    public long getUV(YearMonth yearMonth) {
        // 利用模糊查询获取相关 HyperLogLog 的 key
        final String PATTERN = UV_KEY + yearMonth.format(DateTimeFormatter.ofPattern("yyyy:MM")) + ":*";
        Set<String> keys = stringRedisTemplate.keys(PATTERN);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        // 合计
        long allSize = 0;
        for (String key : keys) {
            Long size = opsForHyperLogLog.size(key);
            allSize += size;
        }
        return allSize;
    }
}
