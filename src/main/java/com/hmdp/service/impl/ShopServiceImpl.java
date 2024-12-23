package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
  @Resource
  private StringRedisTemplate stringRedisTemplate;
  @Resource
  private CacheClient cacheClient;

  @Override
  public Result queryById(Long id) {
//    Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,
//        this::getById, CACHE_SHOP_TTL,TimeUnit.MINUTES);

//    Shop shop = queryWithMutex(id);
    Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
        this::getById, CACHE_SHOP_TTL,TimeUnit.MINUTES);
    if (shop == null) {
      return Result.fail("Shop isn't exist");
    }
    return Result.ok(shop);
  }

  public Shop queryWithMutex(Long id) {
    String key = CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    // cache exist
    if (StrUtil.isNotBlank(shopJson)) {
      return JSONUtil.toBean(shopJson, Shop.class);
    }

    // got null value
    if (shopJson != null) {
      return null;
    }

    // got mutex(set nx)
    String lockKey = LOCK_SHOP_KEY + id;
    Shop shop = null;
    try {
      boolean isLock = tryLock(lockKey);

      if (!isLock) {
        Thread.sleep(50);
        return queryWithMutex(id);
      }
      // request DB if cache non-exist
      shop = getById(id);
      Thread.sleep(200);
      if (shop == null) {
        // write null value into redis
        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
      }

      // rewrite request into cache
      stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      unlock(lockKey);
    }

    return shop;
  }

  private boolean tryLock(String key) {
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  private void unlock(String key) {
    stringRedisTemplate.delete(key);
  }

  public void saveShopToRedis(Long id, Long expiredSeconds) throws InterruptedException {
    Shop shop = getById(id);
    Thread.sleep(200);

    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredSeconds));

    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,
        JSONUtil.toJsonStr(redisData));
  }

  @Override
  @Transactional
  public Result update(Shop shop) {
    Long id = shop.getId();
    if (id == null) {
      return Result.fail("shop id can't be null");
    }
    // update db
    updateById(shop);

    // delete cache
    stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    return Result.ok();
  }

  @Override
  public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
    if (x == null || y == null) {
      Page<Shop> page = query().eq("type_id", typeId)
          .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
      return Result.ok(page.getRecords());
    }

    int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
    int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

    String key = SHOP_GEO_KEY + typeId;
    GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
        key, GeoReference.fromCoordinate(x, y),
        new Distance(5000),
        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
    );
    if (results == null) {
      return Result.ok(Collections.emptyList());
    }

    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
    if (list.size() <= from) {
      return Result.ok(Collections.emptyList());
    }
    List<Long> ids = new ArrayList<>(list.size());
    Map<String, Distance> distanceMap = new HashMap<>(list.size());

    list.stream().skip(from).forEach(result -> {
      String shopIdStr = result.getContent().getName();
      ids.add(Long.valueOf(shopIdStr));

      Distance distance = result.getDistance();
      distanceMap.put(shopIdStr, distance);
    });

    String idStr = StrUtil.join(",", ids);
    List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
    for(Shop shop : shops) {
      shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
    }

    return Result.ok(shops);
  }
}
