package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalIdGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Log4j2
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
    private static final DefaultRedisScript<Long> SECKILL_CHECK_SCRIPT;

    static {
        SECKILL_CHECK_SCRIPT = new DefaultRedisScript<>();
        SECKILL_CHECK_SCRIPT.setLocation(new ClassPathResource("seckill-check.lua"));
        SECKILL_CHECK_SCRIPT.setResultType(Long.class);
    }

    // Stream 名称
    private static final String STREAM_NAME = "seckill:msg-queue";
    // consumer group 名称
    private static final String CONSUMER_GROUP_NAME = "my-group";
    private StreamOperations<String, Object, Object> streamOps;

    // 用于异步处理秒杀订单的线程池
    private final ExecutorService ES = Executors.newSingleThreadExecutor();

    /**
     * 自定义异常类，用于表示 Records 列表中缺少一个有效的 Record 信息
     */
    private static class EmptyRecordsException extends Exception {
        public EmptyRecordsException(String message) {
            super(message);
        }
    }

    private class OrderHandler implements Runnable {

        // Stream 中的消费者名称
        private final String CONSUMER_NAME = UUID.randomUUID().toString(true);

        /**
         * 子线程，从 Stream 中读取订单消息，并生成订单
         * 如果在处理过程中产生异常，将会从对应消费者的 pending-list 中获取一个未确认消息进行处理，作为一种异常恢复机制
         */
        @Override
        public void run() {
            // 获取代理对象
            while (true) {
                // 读取消息（如果消费者不存在，自动创建）
                List<MapRecord<String, Object, Object>> records = null;
                try {
                    records = streamOps.read(Consumer.from(
                                    CONSUMER_GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1L).block(Duration.ofSeconds(20)),
                            StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed()));
                } catch (Exception ex) {
                    log.error(ex.getMessage());
                    break;
                }
                try {
                    // 处理消息
                    handleOrderMsg(records);
                    // 消息处理成功，再次开始循环处理新的消息
                } catch (Exception e) {
                    // 处理消息遇到异常
                    if (e instanceof EmptyRecordsException) {
                        // 没有获取到消息，重新尝试获取
                        log.info("没有获取到新的消息，尝试重新获取");
                        continue;
                    }
                    // 获取一个未确认的消息重新处理
                    while (true) {
                        PendingMessages pendingMessages = streamOps.pending(
                                STREAM_NAME,
                                Consumer.from(CONSUMER_GROUP_NAME, CONSUMER_NAME),
                                Range.unbounded(),
                                1
                        );
                        if (pendingMessages.isEmpty()) {
                            // 没有待处理消息
                            break;
                        }
                        PendingMessage pendingMessage = pendingMessages.get(0);
                        RecordId id = pendingMessage.getId();
                        List<MapRecord<String, Object, Object>> records2 = streamOps.range(STREAM_NAME, Range.closed(id.toString(), id.toString()));
                        try {
                            handleOrderMsg(records2);
                            // 成功处理掉一个待处理任务，结束错误处理流程
                            break;
                        } catch (Exception ex) {
                            if (ex instanceof EmptyRecordsException) {
                                // 没有获取到消息，尝试重新获取
                                log.error("没有获取到未确认消息，尝试重新获取");
                                continue;
                            }
                            // 处理消息出错，尝试再次获取一个待处理消息并进行处理
                            log.error(ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }

        /**
         * 处理订单消息
         *
         * @param records 一个 MapRecord 列表，包含至少一条有效的 MapRecord 信息
         * @throws EmptyRecordsException 当缺少一条有效的 MapRecord 时抛出
         * @throws InterruptedException
         */
        private void handleOrderMsg(List<MapRecord<String, Object, Object>> records) throws InterruptedException, EmptyRecordsException {
            if (records == null || records.isEmpty()) {
                // 没有获取到指定消息
                throw new EmptyRecordsException("没有获取到指定消息");
            }
            MapRecord<String, Object, Object> record = records.get(0);
            Map<Object, Object> map = record.getValue();
            VoucherOrder voucherOrder = new VoucherOrder();
            BeanUtil.fillBeanWithMap(map, voucherOrder, true);
            createOrderWithLock(voucherOrder);
            // 确认消息
            streamOps.acknowledge(CONSUMER_GROUP_NAME, record);
            // 处理订单成功，结束异常订单处理流程
            log.info("订单处理成功");
        }

        /**
         * 用加锁的方式创建订单（一人一单锁）
         *
         * @param voucherOrder 秒杀券订单信息
         * @throws InterruptedException
         */
        private void createOrderWithLock(VoucherOrder voucherOrder) throws InterruptedException {
            VoucherOrderServiceImpl proxy = applicationContext.getBean(VoucherOrderServiceImpl.class);
            RLock lock = redissonClient.getLock("seckill:lock:" + voucherOrder.getUserId());
            boolean isLocked = lock.tryLock(10, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    proxy.doCreateOrder(voucherOrder);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    public boolean consumerGroupExists(String streamKey, String groupName) {
        try {
            stringRedisTemplate.opsForStream().groups(streamKey)
                    .stream()
                    .anyMatch(g -> groupName.equals(g.groupName()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在当前ben对象初始化后启动订单处理线程
     */
    @PostConstruct
    public void afterInit() {
        streamOps = stringRedisTemplate.opsForStream();
        // 如果没有消费者组，创建
        if (!consumerGroupExists(STREAM_NAME, CONSUMER_GROUP_NAME)) {
            streamOps.createGroup(STREAM_NAME, ReadOffset.from("$"), CONSUMER_GROUP_NAME);
        }
        // 启动子线程处理订单
        ES.execute(new OrderHandler());
    }

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
        // 通过 Redis 检查秒杀资格
        Long userId = UserHolder.getUser().getId();
        // 分配一个新的订单id
        long orderId = globalIdGenerator.genGlobalId("voucher-order");
        Long res = stringRedisTemplate.execute(
                SECKILL_CHECK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                Long.toString(orderId));
        if (res == null) {
            // 优惠券秒杀资格检查脚本出错
            return Result.fail("缺少库存");
        }
        if (res != 0) {
            String errMsg = "缺少库存";
            if (res == 2) {
                errMsg = "已经抢购过该优惠券，不能重复抢购";
            }
            return Result.fail(errMsg);
        }
        return Result.ok(orderId);
    }

    @Transactional
    public void doCreateOrder(VoucherOrder voucherOrder) {
        // 检查用户是否已经抢购过该优惠券
        Integer count = this.query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("已经抢购过优惠券，不能重复抢购");
            return;
        }
        // 扣减库存时检查
        boolean res = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0) // 只要库存大于0都可以提交更新
                .update();
        if (!res) {
            log.error("缺少库存");
            return;
        }
        this.save(voucherOrder);
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
