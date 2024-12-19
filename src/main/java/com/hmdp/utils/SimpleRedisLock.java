package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.yaml.snakeyaml.events.Event;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

  private String name;
  private StringRedisTemplate stringRedisTemplate;

  public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  private static final String KEY_PREFIX = "lock:";
  private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

  @Override
  public boolean tryLock(long timeoutSec) {
    String threadID = ID_PREFIX + Thread.currentThread().getId();
    Boolean success = stringRedisTemplate.opsForValue()
        .setIfAbsent(KEY_PREFIX + name, threadID, timeoutSec, TimeUnit.SECONDS);

    // avoid auto unboxing appear null pointer
    // return success
    return Boolean.TRUE.equals(success);
  }

  @Override
  public void unlock() {
    String threadID = ID_PREFIX + Thread.currentThread().getId();
    String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    if (threadID.equals(id)) {
      stringRedisTemplate.delete(KEY_PREFIX + name);
    }
  }
}
