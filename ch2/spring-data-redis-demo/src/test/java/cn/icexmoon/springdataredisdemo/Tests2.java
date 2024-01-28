package cn.icexmoon.springdataredisdemo;

import cn.icexmoon.springdataredisdemo.pojo.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : spring-data-redis-demo
 * @Package : cn.icexmoon.springdataredisdemo
 * @ClassName : .java
 * @createTime : 2024/1/27 20:25
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
@SpringBootTest
public class Tests2 {
    @Autowired
    private StringRedisTemplate redisTemplate;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void test() throws JsonProcessingException {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        User user = new User("Jack", 18);
        String jsonUser = mapper.writeValueAsString(user);
        ops.set("user:3", jsonUser);
        jsonUser = ops.get("user:3");
        user = mapper.readValue(jsonUser, User.class);
        System.out.println(user);
    }
}
