package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2024/2/22 15:19
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@SpringBootTest
public class GlobalIdGeneratorTests {
    @Autowired
    private GlobalIdGenerator globalIdGenerator;

    @Test
    public void test() throws InterruptedException {
        final int THREAD_NUM = 30;
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_NUM);
        ExecutorService es = Executors.newFixedThreadPool(THREAD_NUM);
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                long orderID = globalIdGenerator.genGlobalId("order");
                System.out.println(orderID);
            }
            countDownLatch.countDown();
        };
        LocalDateTime begin = LocalDateTime.now();
        for (int i = 0; i < THREAD_NUM; i++) {
            es.execute(task);
        }
        countDownLatch.await();
        LocalDateTime end = LocalDateTime.now();
        long millis = Duration.between(begin, end).toMillis();
        System.out.println("共耗时" + millis + "毫秒");
    }
}
