package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2024/2/21 14:27
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : Redis 缓存客户端
 */
@Component
public class RedisCacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_UPDATE_ES = Executors.newFixedThreadPool(10);

    /**
     * 设置缓存
     *
     * @param key     缓存 key
     * @param value   缓存 value
     * @param timeout 过期时间
     */
    public void set(String key, Object value, Duration timeout) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout);
    }

    /**
     * 设置缓存（逻辑过期时间）
     *
     * @param key     key
     * @param value   value
     * @param timeout 逻辑过期时间
     */
    public void setWithLogicalTimeout(String key, Object value, Duration timeout) {
        RedisCache<Object> redisCache = new RedisCache<Object>();
        redisCache.setData(value);
        redisCache.setExpire(LocalDateTime.now().plus(timeout));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisCache));
    }

    /**
     * 从 Redis 缓存获取一般数据（用空对象解决缓存穿透问题）
     *
     * @param cls                 数据对应的 Class 对象
     * @param keyPrefix           缓存 key 的前缀
     * @param id                  缓存 key 的 id
     * @param getDataWithoutCache 缓存不存在时需要调用并获取数据的函数
     * @param timeout             如果缓存中没有，重新后缓存的过期时间
     * @param <R>                 返回值类型
     * @param <ID>                id 类型
     * @return 获取到的数据
     */
    public <R, ID> R getWithCachePenetration(Class<R> cls, String keyPrefix, ID id, Function<ID, R> getDataWithoutCache, Duration timeout) {
        // 先从 Redis 中查询
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.isEmpty(json)) {
            return JSONUtil.toBean(json, cls);
        }
        // 如果从缓存中查询到空对象，表示数据不存在
        if ("".equals(json)) {
            return null;
        }
        // Redis 中没有，从数据库查
        R data = getDataWithoutCache.apply(id);
        if (data != null) {
            // 查询到数据，缓存数据
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), timeout);
        } else {
            // 没有查询到数据，缓存空对象
            stringRedisTemplate.opsForValue().set(key, "", timeout);
        }
        return data;
    }

    /**
     * 从 Redis 缓存获取热点数据（用互斥锁解决缓存击穿问题）
     *
     * @param cls                 数据对应的 class 对象
     * @param keyPrefix           缓存 key 的前缀
     * @param id                  缓存 key 的 id
     * @param getDataWithoutCache 如果缓存不存在，需要提供一个函数以获取数据
     * @param timeout             缓存重建时设置的过期时间
     * @param <R>                 数据类型
     * @param <ID>                id 类型
     * @return 查询到的数据
     */
    public <R, ID> R getWithCacheBreakdown(Class<R> cls, String keyPrefix, ID id, Function<ID, R> getDataWithoutCache, Duration timeout) {
        // 先查询是否存在缓存
        String key = CACHE_SHOP_KEY + id;
        String jsonData = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.isEmpty(jsonData)) {
            // 缓存中有对象，返回
            return JSONUtil.toBean(jsonData, cls);
        }
        // 如果从缓存中查询到空对象，表示数据不存在
        if ("".equals(jsonData)) {
            return null;
        }
        // 缓存不存在，尝试获取锁，并创建缓存
        final String lockName = "lock:" + keyPrefix + id;
        try {
            if (!lock(lockName)) {
                // 获取互斥锁失败，休眠一段时间后重试
                Thread.sleep(50);
                return getWithCacheBreakdown(cls, keyPrefix, id, getDataWithoutCache, timeout);
            }
            // 获取互斥锁成功，创建缓存
            R data = getDataWithoutCache.apply(id);
            if (data != null) {
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), CACHE_SHOP_TTL);
            } else {
                // 缓存空对象到缓存中
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL);
            }
            return data;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockName);
        }
    }

    /**
     * 从 Redis 缓存获取热点数据（用逻辑过期解决缓存击穿问题）
     *
     * @param cls                 数据对应的 class 对象
     * @param keyPrefix           缓存 key 的前缀
     * @param id                  缓存 key 的 id
     * @param getDataWithoutCache 如果缓存过期，需要提供一个函数以获取最新数据
     * @param timeout             缓存重建时设置的逻辑过期时间
     * @param <R>                 数据类型
     * @param <ID>                id 类型
     * @return 查询到的数据
     */
    public <R, ID> R getWithLogicalTimeout(Class<R> cls, String keyPrefix, ID id, Function<ID, R> getDataWithoutCache, Duration timeout) {
        //检查缓存是否存在
        String key = CACHE_SHOP_KEY + id;
        String jsonData = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(jsonData)) {
            // 缓存不存在(热点数据需要预热)
            return null;
        }
        // 缓存存在，检查是否过期
        RedisCache redisCache = JSONUtil.toBean(jsonData, RedisCache.class);
        R data = JSONUtil.toBean((JSONObject) redisCache.getData(), cls);
        if (redisCache.getExpire().isBefore(LocalDateTime.now())) {
            // 如果过期，尝试获取互斥锁
            final String LOCK_NAME = "lock:" + keyPrefix + id;
            if (lock(LOCK_NAME)) {
                // 获取互斥锁后，单独启动线程更新缓存
                CACHE_UPDATE_ES.execute(() -> {
                    try {
                        //重建缓存
                        R data2 = getDataWithoutCache.apply(id);
                        this.setWithLogicalTimeout(key, data2, timeout);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        unlock(LOCK_NAME);
                    }
                });
            }
        }
        // 无论是否过期，返回缓存对象中的信息
        return data;
    }

    /**
     * 用 Redis 创建互斥锁
     *
     * @param name 锁名称
     * @return 成功/失败
     */
    private boolean lock(String name) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(name, "1", Duration.ofSeconds(10));
        return BooleanUtil.isTrue(result);
    }

    /**
     * 删除 Redis 互斥锁
     *
     * @param name 锁名称
     */
    private void unlock(String name) {
        stringRedisTemplate.delete(name);
    }
}
