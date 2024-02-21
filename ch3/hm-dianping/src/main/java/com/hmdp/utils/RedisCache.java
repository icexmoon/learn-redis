package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2024/2/18 17:54
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : Redis 缓存对象
 */
@Data
public class RedisCache<T> {
    private LocalDateTime expire; //逻辑过期时间
    private T data; // 数据
}
