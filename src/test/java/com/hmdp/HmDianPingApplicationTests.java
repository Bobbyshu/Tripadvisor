package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HmDianPingApplicationTests {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Test
  void testRedisConnection() {
    // 测试 Redis 是否能够正常工作
    assertNotNull(stringRedisTemplate, "StringRedisTemplate should not be null");

    // 测试写入和读取 Redis 数据
    ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
    ops.set("testKey", "testValue");

    // 验证 Redis 是否成功存储数据
    String value = ops.get("testKey");
    assertEquals("testValue", value, "The value from Redis should match the expected value.");
  }
}
