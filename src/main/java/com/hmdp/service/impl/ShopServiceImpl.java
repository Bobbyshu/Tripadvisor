package com.hmdp.service.impl;

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
    String key = CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    // cache exist
    if (StrUtil.isNotBlank(shopJson)) {
      Shop shop = JSONUtil.toBean(shopJson, Shop.class);
      return Result.ok(shop);
    }

    // got null value
    if (shopJson != null) {
      return Result.fail("shop info non-exist");
    }

    // request DB if cache non-exist
    Shop shop = getById(id);
    if (shop == null) {
      // write null value into redis
      stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
      return Result.fail("Shop with current ID non-exist");
    }

    // rewrite request into cache
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

    return Result.ok(shop);
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
