package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
}
