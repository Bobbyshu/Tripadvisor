package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryTypeList() {
    List<String> lists = stringRedisTemplate.opsForList().range(CACHE_SHOP_KEY, 0, -1);
    List<ShopType> typeList = new ArrayList<>();

    // got cache
    assert lists != null;
    if (!lists.isEmpty()) {
      for (String list : lists) {
        ShopType shopType = JSONUtil.toBean(list, ShopType.class);
        typeList.add(shopType);
      }
      return Result.ok(typeList);
    }

    // Direct query
    List<ShopType> shopTypeList = query().orderByAsc("sort").list();
    if (shopTypeList.isEmpty()) {
      return Result.fail("Can't find this type");
    }

    // transfer to json string
    for (ShopType shopType : shopTypeList) {
      String jsonStr = JSONUtil.toJsonStr(shopType);
      lists.add(jsonStr);
    }
    // write into cache
    stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_KEY,lists);

    return Result.ok(shopTypeList);
  }
}
