package com.lzx;

import cn.hutool.core.util.RandomUtil;
import com.lzx.entity.Shop;
import com.lzx.mapper.ShopMapper;
import com.lzx.redis.RedisConstants;
import com.lzx.redis.RedisIdWorker;
import com.lzx.service.impl.ShopServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
class ZxdpApplicationTests {

    private final ShopServiceImpl shopService;
    private final ShopMapper shopMapper;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop2Redis() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() {
        long id = redisIdWorker.nextId("order:");
        System.out.println(id);
    }

    @Test
    void testGeo() {
        // 获取所有店铺
        List<Shop> shops = shopMapper.selectList(null);
        // 将店铺坐标存储到 Redis 中
//        for (Shop shop : shops) {
//            stringRedisTemplate.opsForGeo().add(
//                    RedisConstants.SHOP_GEO_KEY + shop.getTypeId(),
//                    new Point(shop.getX(), shop.getY()),
//                    shop.getId().toString()
//            );
//        }
        // Map<String, List<RedisGeoCommands.GeoLocation<String>>> result =
        shops.stream()
                .collect(
                        Collectors.groupingBy(
                                shop -> String.valueOf(shop.getTypeId()),  // key转为字符串类型
                                Collectors.mapping(
                                        shop -> new RedisGeoCommands.GeoLocation<>(
                                                shop.getId().toString(),  // 地理位置标识
                                                new Point(
                                                        shop.getX(),  // 经度
                                                        shop.getY()    // 纬度
                                                )
                                        ),
                                        Collectors.toList()  // 收集为GeoLocation列表
                                )
                        )
                )
                .forEach((typeId, locations) -> {  // 直接在收集结果上调用forEach
                    String redisKey = RedisConstants.SHOP_GEO_KEY + typeId;
                    stringRedisTemplate.opsForGeo().add(redisKey, locations);
                });
    }

    @Test
    void testHyperLogLog() {
        // 测试 HyperLogLog 统计独立用户数
        int arrLen = 1000;
        int count = 1000000;
        String key = "hll";
        String[] values = new String[arrLen];
        int j;
        for (int i = 0; i < count; i++) {
            j = i % arrLen;
            values[j] = "user_" + i;
            if (j == arrLen - 1) {
                stringRedisTemplate.opsForHyperLogLog().add(key, values);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size(key);
        // 997593
        System.out.println("size:" + size);
    }
}
