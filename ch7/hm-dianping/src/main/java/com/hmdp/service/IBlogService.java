package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.BLOG_MAILBOX;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    /**
     * 给探店笔记点赞
     *
     * @param id 探店笔记 id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 获取最早点赞的5个人
     *
     * @param id 探店笔记id
     * @return 最早点赞的5个人
     */
    Result queryBlogLikes(Long id);

    /**
     * 获取指定用户的笔记
     *
     * @param uid
     * @return
     */
    List<Blog> getUserBlogs(Long uid, Long current);

    /**
     * 发布探店笔记
     *
     * @param blog 探店笔记
     * @return
     */
    Result publishBlog(Blog blog);

    /**
     * 获取当前用户的探店笔记的信息流
     *
     * @param lastId 上次查询时的最小时间戳
     * @param offset 上次查询的相同元素偏移量
     * @return 探店笔记信息流
     */
    Result queryBlogsFeed(Long lastId, Integer offset);

    /**
     * 维护 Redis 中的探店笔记点赞信息
     */
    class RedisBlogLike {
        // 用户维护探店笔记已经点赞过的用户 sorted set 的 key
        private final String key;
        private final ZSetOperations<String, String> opsForZSet;

        /**
         * @param stringRedisTemplate redis API
         * @param blogId              探店笔记 id
         */
        public RedisBlogLike(StringRedisTemplate stringRedisTemplate, long blogId) {
            key = BLOG_LIKED_KEY + blogId;
            opsForZSet = stringRedisTemplate.opsForZSet();
        }

        /**
         * 判断指定用户是否已经给探店笔记点过赞了
         *
         * @param userId 用户id
         * @return 是否已经点过赞
         */
        public boolean isLiked(long userId) {
            Double score = opsForZSet.score(key, Long.toString(userId));
            // 如果能从 sorted set 中获取到元素对应的 score，说明存在该元素
            return score != null;
        }

        /**
         * 点赞
         *
         * @param userId 点赞的用户 id
         */
        public void like(long userId) {
            // 将用户 id 放入 Set
            opsForZSet.add(key, Long.toString(userId), System.currentTimeMillis());
        }

        /**
         * 取消点赞
         *
         * @param userId 取消点赞的用户 id
         */
        public void unLike(long userId) {
            // 将用户 id 从 Set 中移除
            opsForZSet.remove(key, Long.toString(userId));
        }

        /**
         * 获取最早点赞的N个用户id
         *
         * @param i 要获取的用户 id 数
         * @return 最早点赞的n个用户id
         */
        public Set<String> getEarliestLikes(int i) {
            return opsForZSet.range(key, 0, i);
        }
    }

    /**
     * 基于 Redis 实现的粉丝探店笔记收件箱
     */
    class RedisBlogMailbox {
        // 指定用户收件箱在 Redis 中的 key
        private final String key;
        private final ZSetOperations<String, String> opsForZSet;

        /**
         * @param stringRedisTemplate Redis API
         * @param fansUserId          粉丝的用户id
         */
        public RedisBlogMailbox(StringRedisTemplate stringRedisTemplate, long fansUserId) {
            opsForZSet = stringRedisTemplate.opsForZSet();
            key = BLOG_MAILBOX + fansUserId;
        }

        /**
         * 向粉丝推送有新的探店笔记发布
         *
         * @param blogId 探店笔记 id
         */
        public void sendBlog(long blogId) {
            // 获取当前时间戳
            long millis = System.currentTimeMillis();
            opsForZSet.add(key, Long.toString(blogId), millis);
        }

        /**
         * 分页读取探店笔记 id
         *
         * @param maxTimestamp 读取范围的最大时间戳
         * @param offset       上次读取的相同元素偏移量
         * @param count        页宽
         * @return 分页查询结果
         */
        public ScrollResult<Long> pageRead(Long maxTimestamp, Integer offset, int count) {
            // 如果最大时间戳未指定，使用当前时间戳
            if (maxTimestamp == null) {
                maxTimestamp = System.currentTimeMillis();
            }
            // 如果相同元素偏移量未指定，视作第一次请求，设置为0
            if (offset == null) {
                offset = 0;
            }
            Set<ZSetOperations.TypedTuple<String>> blogTuples = opsForZSet.reverseRangeByScoreWithScores(key, 0, maxTimestamp, offset, count);
            if (blogTuples == null) {
                return null;
            }
            long minTime = 0; // 本次查询的最小时间戳
            int nextOffset = 0; // 本次查询的末尾相同元素个数
            for (ZSetOperations.TypedTuple<String> blogTuple : blogTuples) {
                // 获取当前时间戳
                long blogTime = Long.parseLong(Objects.requireNonNull(blogTuple.getValue()));
                // 比较本次时间戳与上次时间戳
                if (blogTime == minTime) {
                    // 时间戳相同的连续元素出现，偏移量自增
                    nextOffset++;
                } else {
                    // 时间戳与上次时间戳不同，重置偏移量
                    nextOffset = 0;
                }
                // 本次时间戳设置为最小时间戳
                minTime = blogTime;
            }
            List<Long> ids = blogTuples.stream().map(tuple -> Long.valueOf(Objects.requireNonNull(tuple.getValue()))).collect(Collectors.toList());
            ScrollResult<Long> scrollResult = new ScrollResult<>();
            scrollResult.setList(ids);
            scrollResult.setMinTime(minTime);
            scrollResult.setOffset(nextOffset + 1);
            return scrollResult;
        }
    }
}
