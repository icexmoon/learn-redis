package com.hmdp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
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
 * @createTime : 2024/1/28 16:06
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从头信息获取 token
        String token = request.getHeader("Authorization");
        if (ObjectUtils.isEmpty(token)) {
            // 缺少 token
            response.setStatus(401);
            return false;
        }
        // 从 Redis 获取用户信息
        String jsonUser = this.stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY + token);
        if (StringUtils.isEmpty(jsonUser)){
            response.setStatus(401);
            return false;
        }
        UserDTO userDTO = OBJECT_MAPPER.readValue(jsonUser, UserDTO.class);
        if (userDTO == null) {
            response.setStatus(401);
            return false;
        }
        // 将用户信息保存到 ThreadLocal
        UserHolder.saveUser(userDTO);
        // 刷新 token 有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
