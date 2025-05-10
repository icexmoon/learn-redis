package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2024/1/28 19:43
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 刷新 token 的拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果请求头中有 token，且 redis 中有 token 相关的用户信息，刷新其有效期
        String token = request.getHeader("Authorization");
        if (ObjectUtils.isEmpty(token)) {
            return true;
        }
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LOGIN_USER_KEY + token))) {
            stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL);
        }
        return true;
    }
}
