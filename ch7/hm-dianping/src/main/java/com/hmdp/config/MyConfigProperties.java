package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.config
 * @ClassName : .java
 * @createTime : 2025/5/4 17:37
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@Configuration
@ConfigurationProperties(prefix = "my-config")
@Data
public class MyConfigProperties {
    @Data
    public static class RedisConfig{
        private String host;
        private String port;
    }
    private RedisConfig redis1;
    private RedisConfig redis2;
}
