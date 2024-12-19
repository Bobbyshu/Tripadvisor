package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HmDianPingApplicationTests {
  @Resource
  private CacheClient cacheClient;
  @Resource
  private ShopServiceImpl shopService;

  @Resource
  private RedisIdWorker redisIdWorker;

  private ExecutorService es = Executors.newFixedThreadPool(500);
  @Test
  void testIdWorker() {
    Runnable task = () -> {
      for (int i = 0; i < 100; i++) {
        long id = redisIdWorker.nextId("order");
        System.out.println("id = " + id);
      }
    };

    for (int i = 0; i < 300; i++) {
      es.submit(task);
    }
  }

  @Test
  void testSave() throws InterruptedException {
    Shop shop = shopService.getById(1L);
    cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
  }
}
