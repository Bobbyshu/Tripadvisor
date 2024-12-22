package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import io.lettuce.core.api.async.RedisGeoAsyncCommands;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HmDianPingApplicationTests {
  @Resource
  private CacheClient cacheClient;
  @Resource
  private ShopServiceImpl shopService;

  @Resource
  private RedisIdWorker redisIdWorker;

  @Resource
  private StringRedisTemplate stringRedisTemplate;

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

  @Test
  void loadShopData() {
    List<Shop> list = shopService.list();
    Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

    for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
      Long typeId = entry.getKey();
      String key = SHOP_GEO_KEY + typeId;
      List<Shop> value = entry.getValue();
      List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

      for (Shop shop : value) {
        locations.add(new RedisGeoCommands.
            GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
      }

      stringRedisTemplate.opsForGeo().add(key, locations);
    }
  }
}
