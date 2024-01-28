package cn.icexmoon.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : jedis-demo
 * @Package : cn.icexmoon.jedis
 * @ClassName : .java
 * @createTime : 2024/1/27 17:15
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 创建 Jedis 连接的工具类
 */
public class JedisConnectionFactory {
    // Jedis 连接池
    private static JedisPool jedisPool;

    // 初始化 Jedis 连接池
    static {
        // 设置连接池配置
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        // 最大连接数
        jedisPoolConfig.setMaxTotal(8);
        // 最大空闲连接数
        jedisPoolConfig.setMaxIdle(8);
        // 最小空闲连接数
        jedisPoolConfig.setMinIdle(0);
        // 尝试从连接池中获取空闲连接时的等待时间（如果没有空闲连接），超时会产生错误
        jedisPoolConfig.setMaxWait(Duration.ofSeconds(5));
        // 创建连接池
        jedisPool = new JedisPool(jedisPoolConfig,
                "192.168.0.88", 6379, 1000, "123321");
    }

    /**
     * 返回一个空闲的 Redis 连接实例
     * @return Redis 连接实例
     */
    public static Jedis getJedisConnection() {
        return jedisPool.getResource();
    }
}
