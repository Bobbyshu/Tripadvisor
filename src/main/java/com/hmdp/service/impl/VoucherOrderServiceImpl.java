package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  @Resource
  private ISeckillVoucherService seckillVoucherService;

  @Resource
  private RedisIdWorker redisIdWorker;

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Resource
  private RedissonClient redissonClient;

  private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
  static {
    SECKILL_SCRIPT = new DefaultRedisScript<>();
    SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    SECKILL_SCRIPT.setResultType(Long.class);
  }

  @Override
  public Result seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();
    Long result = stringRedisTemplate.execute(
        SECKILL_SCRIPT,
        Collections.emptyList(),
        voucherId.toString(), userId.toString()
    );
    int res = result.intValue();
    if (res != 0) {
      return Result.fail(res == 1 ? "no enough stock" : "can't order twice");
    }

    long orderId = redisIdWorker.nextId("order");
    return Result.ok(orderId);
  }

//  @Override
//  public Result seckillVoucher(Long voucherId) {
//    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//    // flash sale isn't start
//    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//      return Result.fail("flash sale isn't start");
//    }
//
//    // flash sale end
//    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//      return Result.fail("flash sale ended");
//    }
//
//    if (voucher.getStock() < 1) {
//      return Result.fail("stock sold out!");
//    }
//
//    Long userId = UserHolder.getUser().getId();
////    SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//    RLock lock = redissonClient.getLock("lock:order" + userId);
//
//    boolean isLock = lock.tryLock();
//
//    if (!isLock) {
//      return Result.fail("Each user can only order once!");
//    }
//
//    try {
//      IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//      return proxy.createVoucherOrder(voucherId);
//    } finally {
//      lock.unlock();
//    }
//  }

  @Transactional
  public Result createVoucherOrder(Long voucherId) {
    Long userId = UserHolder.getUser().getId();

    int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
      return Result.fail("You have purchased before");
    }

    boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId)
        .gt("stock", 0)
        .update();

    if (!success) {
      return Result.fail("stock sold out!");
    }

    // order id
    VoucherOrder voucherOrder = new VoucherOrder();
    long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);
    // user id
    voucherOrder.setUserId(userId);
    // voucher id
    voucherOrder.setVoucherId(voucherId);
    save(voucherOrder);

    return Result.ok(orderId);

  }
}
