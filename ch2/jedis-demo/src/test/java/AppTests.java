import cn.icexmoon.jedis.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : jedis-demo
 * @Package : PACKAGE_NAME
 * @ClassName : .java
 * @createTime : 2024/1/26 21:04
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description :
 */
public class AppTests {
    private Jedis jedis;

    @BeforeEach
    public void beforeEach() {
//        jedis = new Jedis("192.168.0.88", 6379);
        jedis = JedisConnectionFactory.getJedisConnection();
        jedis.auth("123321");
        jedis.select(0);
    }

    @Test
    public void testString() {
        String res = jedis.set("name", "Jack");
        System.out.println(res);
        res = jedis.get("name");
        System.out.println(res);
    }

    @Test
    public void testHash() {
        jedis.hset("user:1", "name", "Jack");
        jedis.hset("user:1", "age", "18");
        Map<String, String> map = jedis.hgetAll("user:1");
        System.out.println(map);
    }

    @AfterEach
    public void afterEach() {
        if (jedis != null) {
            jedis.close();
        }
    }
}
