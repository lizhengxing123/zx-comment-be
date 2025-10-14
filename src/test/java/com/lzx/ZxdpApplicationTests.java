package com.lzx;

import com.lzx.redis.RedisIdWorker;
import com.lzx.service.ShopService;
import com.lzx.service.impl.ShopServiceImpl;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
class ZxdpApplicationTests {

    private final ShopServiceImpl shopService;
    private final RedisIdWorker redisIdWorker;

    @Test
    void testSaveShop2Redis() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() {
        long id = redisIdWorker.nextId("order:");
        System.out.println(id);
    }

}
