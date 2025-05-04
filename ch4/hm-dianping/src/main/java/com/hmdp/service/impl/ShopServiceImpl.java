package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisCache;
import com.hmdp.utils.RedisCacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisCacheClient redisCacheClient;
    private static final ExecutorService CACHE_UPDATE_ES = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
//        return queryWithCachePenetration(id);
//        return queryWithCacheBreakdown(id);
//        return queryWithLogicalExpiration(id);
//        Shop shop = redisCacheClient.getWithLogicalTimeout(Shop.class, CACHE_SHOP_KEY, id, this::getById, Duration.ofSeconds(10));
//        Shop shop = redisCacheClient.getWithCachePenetration(Shop.class, CACHE_SHOP_KEY, id, this::getById, Duration.ofSeconds(50));
        Shop shop = redisCacheClient.getWithCacheBreakdown(Shop.class, CACHE_SHOP_KEY, id, this::getById, Duration.ofSeconds(50));
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 创建店铺缓存
     *
     * @param id       店铺id
     * @param duration 缓存有效时长
     */
    public void saveShopCache(Long id, Duration duration) {
        Shop shop = getById(id);
        RedisCache<Shop> redisCache = new RedisCache<>();
        redisCache.setExpire(LocalDateTime.now().plus(duration));
        redisCache.setData(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisCache));
    }

    /**
     * 用逻辑过期解决缓存击穿问题
     *
     * @return
     */
    private Result queryWithLogicalExpiration(Long id) {
        //检查缓存是否存在
        String jsonShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StringUtils.isEmpty(jsonShop)) {
            // 缓存不存在
            return Result.fail("店铺不存在");
        }
        // 缓存存在，检查是否过期
        RedisCache<Shop> redisCache = JSONUtil.toBean(jsonShop, new TypeReference<RedisCache<Shop>>() {
        }, true);
        if (redisCache.getExpire().isBefore(LocalDateTime.now())) {
            // 如果过期，尝试获取互斥锁
            final String LOCK_NAME = LOCK_SHOP_KEY + id;
            if (lock(LOCK_NAME)) {
                // 获取互斥锁后，单独启动线程更新缓存
                CACHE_UPDATE_ES.execute(() -> {
                    try {
                        // 模拟缓存重建的延迟
                        Thread.sleep(200);
                        saveShopCache(id, Duration.ofSeconds(1));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        unlock(LOCK_NAME);
                    }
                });
            }
        }
        // 无论是否过期，返回缓存对象中的信息
        return Result.ok(redisCache.getData());
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

    /**
     * 查询店铺信息-用互斥锁解决缓存击穿
     *
     * @param id
     * @return
     */
    private Result queryWithCacheBreakdown(Long id) {
        // 先查询是否存在缓存
        String jsonShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (!StringUtils.isEmpty(jsonShop)) {
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return Result.ok(shop);
        }
        // 如果从缓存中查询到空对象，表示商铺不存在
        if ("".equals(jsonShop)) {
            return Result.fail("商铺不存在");
        }
        // 缓存不存在，尝试获取锁，并创建缓存
        final String lockName = "lock:shop:" + id;
        try {
            if (!lock(lockName)) {
                // 获取互斥锁失败，休眠一段时间后重试
                Thread.sleep(50);
                return queryWithCacheBreakdown(id);
            }
            // 获取互斥锁成功，创建缓存
            // 模拟长时间才能创建缓存
            Thread.sleep(100);
            Shop shop = this.getById(id);
            if (shop != null) {
                jsonShop = JSONUtil.toJsonStr(shop);
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonShop, CACHE_SHOP_TTL);
                return Result.ok(shop);
            } else {
                // 缓存空对象到缓存中
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL);
                return Result.fail("店铺不存在");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockName);
        }
    }

    /**
     * 查询店铺信息-用空对象解决缓存穿透
     *
     * @param id
     */
    private Result queryWithCachePenetration(Long id) {
        // 先从 Redis 中查询
        String jsonShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (!StringUtils.isEmpty(jsonShop)) {
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return Result.ok(shop);
        }
        // 如果从缓存中查询到空对象，表示商铺不存在
        if ("".equals(jsonShop)) {
            return Result.fail("商铺不存在");
        }
        // Redis 中没有，从数据库查
        Shop shop = this.getById(id);
        if (shop != null) {
            jsonShop = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonShop, CACHE_SHOP_TTL);
            return Result.ok(shop);
        } else {
            // 缓存空对象到缓存中
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL);
            return Result.fail("店铺不存在");
        }
    }

    @Override
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商户id不能为空");
        }
        // 更新商户信息
        this.updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
