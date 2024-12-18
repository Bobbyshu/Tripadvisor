package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

  private final StringRedisTemplate stringRedisTemplate;
  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
  public CacheClient(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  public void set(String key, Object value, Long time, TimeUnit unit) {
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
  }

  public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
    RedisData redisData = new RedisData();
    redisData.setData(value);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
  }

  public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                        Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);

    // cache exist
    if (StrUtil.isNotBlank(json)) {
      return JSONUtil.toBean(json, type);
    }

    // got null value
    if (json != null) {
      return null;
    }

    // request DB if cache non-exist
    R r = dbFallBack.apply(id);
    if (r == null) {
      // write null value into redis
      stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }

    // rewrite request into cache
    this.set(key, r, time, unit);

    return r;
  }

  public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);

    // cache exist
    if (StrUtil.isBlank(json)) {
      return null;
    }

    // deserialize json object
    RedisData redisData = JSONUtil.toBean(json, RedisData.class);
    R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
    LocalDateTime expireTime = redisData.getExpireTime();

    if (expireTime.isAfter(LocalDateTime.now())) {
      return r;
    }

    String lockKey = LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    if (isLock) {
      // open thread
      CACHE_REBUILD_EXECUTOR.submit(() -> {
        // rebuild cache
        try {
          R r1 = dbFallBack.apply(id);
          this.setWithLogicalExpire(key, r1, time, unit);
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          // release lock
          unlock(lockKey);
        }
      });
    }

    return r;
  }

  private boolean tryLock(String key) {
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  private void unlock(String key) {
    stringRedisTemplate.delete(key);
  }
}
