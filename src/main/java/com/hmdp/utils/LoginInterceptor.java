package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {
  private StringRedisTemplate stringRedisTemplate;

  // must use constructor because this class isn't being managed by spring bean
  public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String token = request.getHeader("authorization");
    if (StrUtil.isBlank(token)) {
      response.setStatus(401);
      return false;
    }

    // got user by token
    String key = RedisConstants.LOGIN_USER_KEY + token;
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
    if (userMap.isEmpty()) {
      response.setStatus(401);
      return false;
    }

    // transfer hash data to DTO object
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

    // store user info into Threadlocal
    UserHolder.saveUser(userDTO);

    // update token valid time
    stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
  }
}
