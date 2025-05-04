package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Log4j2
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("不是合法的手机号！");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL);
        // 发送短信
        log.debug("发送短信验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 验证手机号和验证码
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号不合法！");
        }
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码不正确！");
        }
        // 检查用户是否存在
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("phone", loginForm.getPhone());
        User user = this.baseMapper.selectOne(qw);
        if (user == null) {
            user = this.createUserByPhone(loginForm.getPhone());
        }
        // 将用户信息保存到 session
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        try {
            stringRedisTemplate.opsForValue().set(LOGIN_USER_KEY + token,
                    OBJECT_MAPPER.writeValueAsString(userDTO), LOGIN_USER_TTL);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(5));
        this.baseMapper.insert(user);
        return user;
    }
}
