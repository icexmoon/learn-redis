package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    /**
     * 按照地理位置信息查询商铺列表
     *
     * @param typeId  商铺类型
     * @param current 当前页码
     * @param x       经度
     * @param y       纬度
     * @return 商铺列表
     */
    Result queryShopsByType(Integer typeId, Integer current, Double x, Double y);

    /**
     * 商铺地理位置信息
     */
    class RedisShopGeo {
        private final String key;
        private final GeoOperations<String, String> opsForGeo;

        public RedisShopGeo(StringRedisTemplate stringRedisTemplate, long typeId) {
            this.key = SHOP_GEO_KEY + typeId;
            opsForGeo = stringRedisTemplate.opsForGeo();
        }

        /**
         * 分页获取商铺位置信息（按由近到远排列）
         *
         * @param x        当前位置的经度
         * @param y        当前位置的纬度
         * @param distance 查询距离范围
         * @param start    分页起始位置
         * @param limit    返回数据最大条数
         * @return 商铺位置信息列表
         */
        public List<GeoResult<RedisGeoCommands.GeoLocation<Long>>> searchShops(double x, double y, Distance distance, long start, int limit) {
            GeoReference<String> reference = new GeoReference.GeoCoordinateReference<>(x, y);
            RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs();
            args.limit(start + limit);
            args.sort(Sort.Direction.ASC); // 按距离升序排列
            args.includeDistance(); // 返回值包含距离信息
            GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = opsForGeo.search(key, reference, distance, args);
            // 逻辑分页
            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> results = geoResults.getContent();
            if (results.size() <= start) {
                return Collections.emptyList();
            }
            // 只取最后 limit 个商铺位置信息
            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> collect = results.stream()
                    .skip(start)
                    .collect(Collectors.toList());
            // 将 String 类型的 shopId 转换为 long
            List<GeoResult<RedisGeoCommands.GeoLocation<Long>>> longResults = new ArrayList<>(collect.size());
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : collect) {
                RedisGeoCommands.GeoLocation<Long> content = new RedisGeoCommands.GeoLocation<>(Long.valueOf(result.getContent().getName()), result.getContent().getPoint());
                GeoResult<RedisGeoCommands.GeoLocation<Long>> longResult = new GeoResult<>(content, result.getDistance());
                longResults.add(longResult);
            }
            return longResults;
        }
    }
}
