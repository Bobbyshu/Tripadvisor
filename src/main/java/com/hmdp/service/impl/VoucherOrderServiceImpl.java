package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
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

  private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
  private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
  @PostConstruct
  private void init() {
    SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandle());
  }
  private class VoucherOrderHandle implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          VoucherOrder voucherOrder = orderTasks.take();
          handleVoucherOrder(voucherOrder);
        } catch (Exception e) {
          log.error("Error happens: ", e);
        }
      }
    }
  }

  private void handleVoucherOrder(VoucherOrder voucherOrder) {
    Long userId = voucherOrder.getUserId();
    RLock lock = redissonClient.getLock("lock:order" + userId);

    boolean isLock = lock.tryLock();

    if (!isLock) {
      log.error("Can't place order twice!");
      return;
    }

    try {
      return proxy.createVoucherOrder(voucherOrder);
    } finally {
      lock.unlock();
    }
  }

  private IVoucherOrderService proxy;
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
    VoucherOrder voucherOrder = new VoucherOrder();
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setVoucherId(voucherId);

    // add into blocking queue
    orderTasks.add(voucherOrder);
    proxy = (IVoucherOrderService) AopContext.currentProxy();

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
  public void createVoucherOrder(VoucherOrder voucherOrder) {
    Long userId = voucherOrder.getUserId();

    int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
    if (count > 0) {
      log.error("Can't place order twice!");
      return;
    }

    boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherOrder.getVoucherId())
        .gt("stock", 0)
        .update();

    if (!success) {
      log.error("stock sold out!");
      return;
    }

    save(voucherOrder);
  }
}
