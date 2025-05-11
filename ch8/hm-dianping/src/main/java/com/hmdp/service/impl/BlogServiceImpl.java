package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("探店笔记不存在");
        }
        fillExtraInfo(blog);
        return Result.ok(blog);
    }

    /**
     * 对探店笔记填充非数据库生成信息
     *
     * @param blog 探店笔记
     */
    private void fillExtraInfo(Blog blog) {
        fillUserInfo(blog);
        fillLikedInfo(blog);
    }

    /**
     * 对探店笔记填充当前用户是否点赞信息
     *
     * @param blog 探店笔记
     */
    private void fillLikedInfo(Blog blog) {
        // 设置用户是否已点赞信息
        RedisBlogLike redisBlogLike = new RedisBlogLike(stringRedisTemplate, blog.getId());
        blog.setIsLike(redisBlogLike.isLiked(UserHolder.getUser().getId()));
    }

    /**
     * 对探店笔记填充用户信息
     *
     * @param blog 探店笔记
     */
    private void fillUserInfo(Blog blog) {
        // 获取探店笔记对应的用户信息
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result likeBlog(Long id) {
        // 通过 Redis 查询当前用户有没有点赞过
        RedisBlogLike redisBlogLike = new RedisBlogLike(stringRedisTemplate, id);
        Long userId = UserHolder.getUser().getId();
        if (!redisBlogLike.isLiked(userId)) {
            // 没有点赞过，点赞
            // 数据库点赞次数+1
            boolean updateRes = this.update()
                    .setSql("liked=liked+1")
                    .eq("id", id.toString())
                    .update();
            // 如果数据更新成功，Redis 中也进行点赞操作
            if (updateRes) {
                redisBlogLike.like(userId);
                return Result.ok();
            }
            return Result.fail("执行出错");
        }
        // 已经点赞过，取消点赞
        // 数据库点赞次数-1
        boolean updateRes = this.update()
                .setSql("liked=liked-1")
                .eq("id", id.toString())
                .update();
        // 如果数据更新成功，Redis 中也进行取消点赞操作
        if (updateRes) {
            redisBlogLike.unLike(userId);
            return Result.ok();
        }
        return Result.fail("执行出错");
    }

    @Override
    public Result queryBlogLikes(Long id) {
        RedisBlogLike redisBlogLike = new RedisBlogLike(stringRedisTemplate, id);
        Set<String> uids = redisBlogLike.getEarliestLikes(5);
        String ids = String.join(",", uids);
        List<User> users = userService.query().in("id", uids).last(String.format("order by field (id,%s)", ids)).list();
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public List<Blog> getUserBlogs(Long uid, Long current) {
        QueryWrapper<Blog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", uid);
        IPage<Blog> page = new Page<>(current, 10);
        this.page(page, queryWrapper);
        return page.getRecords();
    }

    @Override
    public Result publishBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if (save) {
            // 获取当前用户的粉丝
            List<Long> fansIds = followService.getFans(user.getId());
            // 为粉丝推送探店笔记更新通知
            fansIds.forEach(uid -> {
                RedisBlogMailbox redisBlogMailbox = new RedisBlogMailbox(stringRedisTemplate, uid);
                redisBlogMailbox.sendBlog(blog.getId());
            });
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogsFeed(Long lastId, Integer offset) {
        // 从 Redis 当前用户收件箱分页查询探店笔记id
        RedisBlogMailbox redisBlogMailbox = new RedisBlogMailbox(stringRedisTemplate, UserHolder.getUser().getId());
        ScrollResult<Long> scrollResult = redisBlogMailbox.pageRead(lastId, offset, 5);
        // 向结果中填充博客详情
        List<Blog> blogs = new ArrayList<>(scrollResult.getList().size());
        for (Long blogId : scrollResult.getList()) {
            Blog blog = this.getById(blogId);
            this.fillExtraInfo(blog);
            blogs.add(blog);
        }
        ScrollResult<Blog> result = new ScrollResult<>();
        result.setList(blogs);
        result.setMinTime(scrollResult.getMinTime());
        result.setOffset(scrollResult.getOffset());
        return Result.ok(result);
    }
}
