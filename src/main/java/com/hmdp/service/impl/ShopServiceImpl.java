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

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

    // request DB if cache non-exist
    Shop shop = getById(id);
    if (shop == null) {
      return Result.fail("Shop with current ID non-exist");
    }

    // rewrite request into cache
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));

    return Result.ok(shop);
  }
}
