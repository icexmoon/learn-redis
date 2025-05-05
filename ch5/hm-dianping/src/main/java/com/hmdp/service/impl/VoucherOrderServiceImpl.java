package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalIdGenerator;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private GlobalIdGenerator globalIdGenerator;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 创建优惠券订单
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result createOrder(Long voucherId) {
        // 获取秒杀优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断当前时间是否在优惠券有效期内
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("优惠券抢购未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("优惠券抢购已结束");
        }
        if (voucher.getStock() <= 0) {
            return Result.fail("缺少库存");
        }
        VoucherOrderServiceImpl proxy = applicationContext.getBean(VoucherOrderServiceImpl.class);
        Long userId = UserHolder.getUser().getId();
        // 使用用户标识进行加锁
        String lockName = "lock:voucher-order:" + userId.toString();
        RLock lock = redissonClient.getLock(lockName);
        boolean isLock = false;
        try {
            isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!isLock) {
            return Result.fail("同一用户不能重复抢购");
        }
        try {
            return proxy.doCreateOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result doCreateOrder(Long voucherId) {
        // 检查用户是否已经抢购过该优惠券
        Integer count = this.query()
                .eq("user_id", UserHolder.getUser().getId())
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("已经抢购过优惠券，不能重复抢购");
        }
        // 扣减库存时检查
        boolean res = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 只要库存大于0都可以提交更新
                .update();
        if (!res) {
            return Result.fail("缺少库存");
        }
        // 生成秒杀优惠券订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(globalIdGenerator.genGlobalId("voucher-order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        this.save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
