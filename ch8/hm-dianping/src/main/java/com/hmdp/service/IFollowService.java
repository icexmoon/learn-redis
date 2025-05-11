package com.hmdp.service;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取消关注
     *
     * @param id   要关注的用户id
     * @param flag true 关注，false 取消关注
     * @return 执行结果
     */
    Result follow(Long id, Boolean flag);

    /**
     * 判断当前用户是否已经关注指定用户
     *
     * @param followUserId 指定用户id
     * @return 是否已经关注
     */
    Result isFollowed(Long followUserId);

    /**
     * 当前用户与指定用户共同关注的用户
     *
     * @param uid 指定用户id
     * @return 共同关注的用户
     */
    Result commonFollows(Long uid);

    /**
     * Redis 中指定用户的关注列表
     */
    class RedisFollowList {
        // 关注列表的 Redis key
        private final String key;
        private final SetOperations<String, String> opsForSet;

        /**
         * @param stringRedisTemplate
         * @param userId              指定用户的 id
         */
        public RedisFollowList(StringRedisTemplate stringRedisTemplate, long userId) {
            this.key = getKeyByUserId(userId);
            opsForSet = stringRedisTemplate.opsForSet();
        }

        private String getKeyByUserId(long userId) {
            return FOLLOW_KEY + userId;
        }

        /**
         * 关注
         *
         * @param targetUserId 指定用户 id
         */
        public void follow(long targetUserId) {
            opsForSet.add(key, Long.toString(targetUserId));
        }

        /**
         * 取消关注
         *
         * @param targetUserId 指定用户 id
         */
        public void cancelFollow(long targetUserId) {
            opsForSet.remove(key, Long.toString(targetUserId));
        }

        /**
         * 判断是否已经关注过指定用户
         *
         * @param targetUserId 指定用户 id
         */
        public boolean isFollowed(long targetUserId) {
            Boolean isMember = opsForSet.isMember(key, Long.toString(targetUserId));
            return BooleanUtil.isTrue(isMember);
        }

        /**
         * 返回当前用户与指定用户共同关注的用户列表
         *
         * @param uid 指定用户
         * @return 共同关注的用户列表
         */
        public Set<Long> commonFollows(Long uid) {
            // 用 Set 求交集实现
            Set<String> intersect = opsForSet.intersect(key, getKeyByUserId(uid));
            if (intersect == null || intersect.isEmpty()) {
                return Collections.emptySet();
            }
            return intersect.stream().map(Long::valueOf).collect(Collectors.toSet());
        }
    }

    /**
     * 获取指定用户的粉丝
     *
     * @param uid 指定用户id
     * @return 粉丝id集合
     */
    List<Long> getFans(Long uid);
}
