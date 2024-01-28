package cn.icexmoon.springdataredisdemo;

import cn.icexmoon.springdataredisdemo.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
class SpringDataRedisDemoApplicationTests {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testString() {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        ops.set("name", "王二");
        String val = (String) ops.get("name");
        System.out.println(val);
        ops.set("user:2", new User("Jack", 18));
        User user = (User) ops.get("user:2");
        System.out.println(user);
    }

}
