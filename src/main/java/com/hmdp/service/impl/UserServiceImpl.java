package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  @Override
  public Result sendCode(String phone, HttpSession session) {
    // check phone number
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("Format of phone number is invalid!");
    }

    String code = RandomUtil.randomNumbers(6);
    session.setAttribute("code", code);
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
    Object cacheCode = session.getAttribute("code");
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

    session.setAttribute("user", user);
    return Result.ok();
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
