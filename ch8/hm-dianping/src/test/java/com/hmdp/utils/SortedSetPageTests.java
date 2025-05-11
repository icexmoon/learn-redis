package com.hmdp.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * @createTime : 2025/5/9 19:58
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@SpringBootTest
public class SortedSetPageTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY = "test:zset1";

    @BeforeEach
    public void beforeEach() {
        // 准备测试数据
        ZSetOperations<String, String> opsForZSet = stringRedisTemplate.opsForZSet();
        for (int i = 0; i < 10; i++) {
            opsForZSet.add(KEY, "m" + i, 3);
        }
    }

    @AfterEach
    public void afterEach() {
        // 删除测试数据
        stringRedisTemplate.delete(KEY);
    }

    @Test
    public void testPage() {
        SortedSetPage sortedSetPage = new SortedSetPage(stringRedisTemplate, KEY);
        long currentMax = 1000;
        int page = 1;
        final int COUNT = 2;
        do {
            Set<String> data = sortedSetPage.page(currentMax, page, COUNT);
            if (data == null || data.isEmpty()) {
                break;
            }
            for (String d : data) {
                System.out.println(d);
            }
            page++;
        }
        while (true);
    }
}
