package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_TYPE_LIST_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_TYPE_LIST_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {
        String jsonTypeList = stringRedisTemplate.opsForValue().get(CACHE_TYPE_LIST_KEY);
        if (!StringUtils.isEmpty(jsonTypeList)) {
            List<ShopType> typeList = JSONUtil.toList(jsonTypeList, ShopType.class);
            return Result.ok(typeList);
        }
        List<ShopType> typeList = this
                .query().orderByAsc("sort").list();
        if (!typeList.isEmpty()){
            stringRedisTemplate.opsForValue().set(CACHE_TYPE_LIST_KEY, JSONUtil.toJsonStr(typeList), CACHE_TYPE_LIST_TTL);
        }
        return Result.ok(typeList);
    }
}
