package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.config
 * @ClassName : .java
 * @createTime : 2025/5/4 11:08
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : Redis相关配置
 */
@Configuration
public class RedisConfig {
    @Autowired
    RedisProperties redisProperties;
    @Autowired
    MyConfigProperties myConfig;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        String address = String.format("redis://%s:%s", redisProperties.getHost(), redisProperties.getPort());
        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisProperties.getPassword());
        return Redisson.create(config);
    }

//    @Bean
//    public RedissonClient redissonClient2(){
//        Config config = new Config();
//        String address = String.format("redis://%s:%s",
//                myConfig.getRedis1().getHost(),
//                myConfig.getRedis1().getPort());
//        config.useSingleServer()
//                .setAddress(address);
//        return Redisson.create(config);
//    }
//
//    @Bean
//    public RedissonClient redissonClient3(){
//        Config config = new Config();
//        String address = String.format("redis://%s:%s",
//                myConfig.getRedis2().getHost(),
//                myConfig.getRedis2().getPort());
//        config.useSingleServer()
//                .setAddress(address);
//        return Redisson.create(config);
//    }
}
