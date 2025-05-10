package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/9 19:40
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : SortedSet 分页查询
 */
public class SortedSetPage {

    private final ZSetOperations<String, String> opsForZSet;
    private final String key;

    public SortedSetPage(StringRedisTemplate stringRedisTemplate, String key) {
        opsForZSet = stringRedisTemplate.opsForZSet();
        this.key = key;
    }

    /**
     * 分页查询
     *
     * @param maxScore 查询限定的最大 score
     * @param page     页数
     * @param count    页宽
     * @return
     */
    public Set<String> page(double maxScore, int page, int count) {
        int offset = (page - 1) * count;
        return opsForZSet.reverseRangeByScore(key, 0, maxScore, offset, count);
    }
}
