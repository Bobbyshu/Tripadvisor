package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryById(Long id) {
//    Shop shop = queryWithPassThrough(id);

    Shop shop = queryWithMutex(id);
    if (shop == null) {
      return Result.fail("Shop non-exist");
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

  public Shop queryWithPassThrough(Long id) {
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

    // request DB if cache non-exist
    Shop shop = getById(id);
    if (shop == null) {
      // write null value into redis
      stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }

    // rewrite request into cache
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

    return shop;
  }

  private boolean tryLock(String key) {
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  private void unlock(String key) {
    stringRedisTemplate.delete(key);
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
}
