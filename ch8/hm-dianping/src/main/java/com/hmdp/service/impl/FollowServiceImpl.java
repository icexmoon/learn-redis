package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean flag) {
        // 获取当前用户id
        Long uid = UserHolder.getUser().getId();
        RedisFollowList redisFollowList = new RedisFollowList(stringRedisTemplate, uid);
        if (BooleanUtil.isTrue(flag)) {
            // 关注
            Follow follow = new Follow();
            follow.setUserId(uid);
            follow.setFollowUserId(id);
            boolean res = this.save(follow);
            if (res) {
                // 在 Redis 中关注指定用户
                redisFollowList.follow(id);
            }
        } else {
            // 取消关注
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", uid);
            queryWrapper.eq("follow_user_id", id);
            boolean res = this.remove(queryWrapper);
            if (res) {
                // 在 Redis 中取消关注
                redisFollowList.cancelFollow(id);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        RedisFollowList redisFollowList = new RedisFollowList(stringRedisTemplate, UserHolder.getUser().getId());
        return Result.ok(redisFollowList.isFollowed(followUserId));
    }

    @Override
    public Result commonFollows(Long uid) {
        RedisFollowList redisFollowList = new RedisFollowList(stringRedisTemplate, UserHolder.getUser().getId());
        Set<Long> uids = redisFollowList.commonFollows(uid);
        if (uids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 获取用户详情
        List<User> users = userService.listByIds(uids);
        return Result.ok(users);
    }

    @Override
    public List<Long> getFans(Long uid) {
        QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("follow_user_id", uid);
        List<Follow> follows = list(queryWrapper);
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }
        return follows.stream().map(Follow::getUserId).collect(Collectors.toList());
    }
}
