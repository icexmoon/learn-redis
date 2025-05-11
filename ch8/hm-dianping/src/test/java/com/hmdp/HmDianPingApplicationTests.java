package com.hmdp;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

@Log4j2
@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    public void testSaveShopCache() {
        shopService.saveShopCache(1L, Duration.ofSeconds(1));
    }

    /**
     * 模拟多个用户登录
     */
    @Test
    public void testMultiUsersLogin() throws IOException {
        // 清理已经登录的用户
        Set<String> keys = stringRedisTemplate.keys("login:token:*");
        if (keys != null) {
            for (String key : keys) {
                stringRedisTemplate.delete(key);
            }
        }
        // 读取1000个用户信息
        final int USER_NUM = 1000;
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.ne("phone", "123").last(String.format("limit %d", USER_NUM));
        List<User> users = userService.list(qw);
        List<String> tokens = new ArrayList<>(USER_NUM);
        // 模拟用户登录
        for (User user : users) {
            String token = UUID.randomUUID().toString(true);
            tokens.add(token);
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            stringRedisTemplate.opsForValue().set(LOGIN_USER_KEY + token,
                    OBJECT_MAPPER.writeValueAsString(userDTO), LOGIN_USER_TTL);
        }
        // 将用户token写入测试文件
        Resource resource = resourceLoader.getResource("classpath:");
        String filePath = resource.getURL().getPath() + "tokens.txt"; // 文件路径
        log.info(filePath);
        File file = new File(filePath);
        if (file.exists()) {
            boolean res = file.delete();
            if (!res) {
                log.error("文件删除失败");
                return;
            }
        }
        try (FileWriter fw = new FileWriter(file)) {
            for (String token : tokens) {
                fw.write(token + "\n");
            }
        }
    }

    /**
     * 导入店铺的地理位置信息到 Redis
     */
    @Test
    public void testImportGeoData() {
        // 检查是否已经存在旧数据
        Set<String> keys = stringRedisTemplate.keys(SHOP_GEO_KEY + "*");
        if (keys != null && (!keys.isEmpty())) {
            stringRedisTemplate.delete(keys);
        }
        // 获取店铺信息
        List<Shop> shops = shopService.list();
        // 将店铺按照类型分组
        Map<Long, List<Shop>> groupShops = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 按照分组导入 Redis
        GeoOperations<String, String> opsForGeo = stringRedisTemplate.opsForGeo();
        for (Map.Entry<Long, List<Shop>> entry : groupShops.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> subShops = entry.getValue();
            final String key = SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(subShops.size());
            for (Shop shop : subShops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            opsForGeo.add(key, locations);
        }
    }

}
