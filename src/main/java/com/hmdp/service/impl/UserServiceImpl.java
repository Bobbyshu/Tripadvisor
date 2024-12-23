package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * Service impl
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result sendCode(String phone, HttpSession session) {
    // check phone number
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("Format of phone number is invalid!");
    }

    String code = RandomUtil.randomNumbers(6);
    // store into redis phone->code
    stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
    log.debug("Successfully send verification email! code: {}", code);
    return Result.ok();
  }

  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    // check phone number
    String phone = loginForm.getPhone();
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("Format of phone number is invalid!");
    }
    // server saved code
    String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
    // front end got form code
    String code = loginForm.getCode();
    if (cacheCode == null || !cacheCode.equals(code)) {
      return Result.fail("Verification code error");
    }

    // mybatis plus
    User user = query().eq("phone", phone).one();
    if (user == null) {
      // register
      user = createUserWithPhone(phone);
    }

    // generate token for login
    String token = UUID.randomUUID().toString(true);
    // turn object to hash store
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
        CopyOptions.create().setIgnoreNullValue(true)
            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

    String tokenKey = LOGIN_USER_KEY + token;
    stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
    stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
    return Result.ok(token);
  }

  @Override
  public Result sign() {
    Long userId = UserHolder.getUser().getId();
    LocalDateTime now = LocalDateTime.now();

    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = USER_SIGN_KEY + userId + keySuffix;

    int dayOfMonth = now.getDayOfMonth();
    stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
    return Result.ok();
  }

  @Override
  public Result signCount() {
    Long userId = UserHolder.getUser().getId();
    LocalDateTime now = LocalDateTime.now();

    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = USER_SIGN_KEY + userId + keySuffix;

    // get current day
    int dayOfMonth = now.getDayOfMonth();
    // get current history of check-in until today
    List<Long> results = stringRedisTemplate.opsForValue().bitField(
        key, BitFieldSubCommands.create()
            .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
    );

    if (results == null || results.isEmpty()) {
      return Result.ok(0);
    }

    Long num = results.get(0);
    if (num == null || num == 0) {
      return Result.ok(0);
    }

    int cnt = 0;
    while (true) {
      // check last bit is 0 or not
      if ((num & 1) == 0) {
        // 0 for non check in
        break;
      } else {
        ++cnt;
      }
      num >>>= 1;
    }

    return Result.ok(cnt);
  }

  private User createUserWithPhone(String phone) {
    User user = new User();
    user.setPhone(phone);
    user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

    // mybatis plus
    save(user);
    return user;
  }
}
