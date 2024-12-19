package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

  private String name;
  private StringRedisTemplate stringRedisTemplate;

  public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  private static final String KEY_PREFIX = "lock:";
  @Override
  public boolean tryLock(long timeoutSec) {
    long threadID = Thread.currentThread().getId();
    Boolean success = stringRedisTemplate.opsForValue()
        .setIfAbsent(KEY_PREFIX + name, threadID + "", timeoutSec, TimeUnit.SECONDS);

    // avoid auto unboxing appear null pointer
    // return success
    return Boolean.TRUE.equals(success);
  }

  @Override
  public void unlock() {
    stringRedisTemplate.delete(KEY_PREFIX + name);
  }
}
