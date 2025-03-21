package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;

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

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private ConfigurationPropertiesAutoConfiguration configurationPropertiesAutoConfiguration;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        /*
         * 因为此时用户下单在并发状态下可以重复下单造成了黄牛的一个问题，所以需要一人一单
         * */
        Long userId = UserHolder.getUser().getId();
        /**
         * synchronized (userId.toString().intern()) {
         *             //通过代理对象，进行方法调用，避免事务的失败
         *             IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
         *             return proxy.createVoucherOrder(voucherId);
         *         }
         */

        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        if (!lock.tryLock(5)) {
            return Result.fail("服务器繁忙");
        }
        try {
            //拿到代理对象，进⾏⽅法调⽤，避免事务失效。
            IVoucherOrderService proxy = (IVoucherOrderService)
                    AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }
        }

        @Transactional
        public Result createVoucherOrder (Long voucherId){
            //一人一单
            Long userId = UserHolder.getUser().getId();
            LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper
                    .eq(VoucherOrder::getUserId, voucherId)
                    .eq(VoucherOrder::getVoucherId, voucherId);
            int count = this.count(queryWrapper);

            if (count > 0) {
                return Result.fail("您已购买过此商品");
            }
            //扣除库存
            boolean success = seckillVoucherService
                    .update()
                    .setSql("stock=stock-1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)//乐观锁解决超卖问题（CAS法）
                    .update();
            if (!success) {
                return Result.fail("被抢光了 >_< ");
            }
            //创建订单
            try {
                Class<?> clazz = Class.forName("com.hmdp.entity.VoucherOrder");
                Constructor<?> constructor = clazz.getConstructor();
                VoucherOrder vocherOrder = (VoucherOrder) constructor.newInstance();
                long orderId = redisIdWorker.createId("order");
                vocherOrder.setId(orderId).setUserId(userId).setVoucherId(voucherId);
                this.save(vocherOrder);
                return Result.ok(orderId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }
