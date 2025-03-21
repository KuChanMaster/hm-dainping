package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.sql.Time;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class SimpleRedisLock implements ILock {
    private String name;//锁的名称
    private StringRedisTemplate stringRedisTemplate;

    public static final String KEY_PREFIX = "lock";//锁的前缀

/**
 * 当线程1获取锁后，由于业务阻塞，线程1的锁超时释放了，
 * 这时候线程2趁虚而入拿到了锁，然后此时线程1业务完成了，
 * 然后把线程2刚刚获取的锁给释放了，这时候线程3又趁虚而入拿到了锁，
 * 这就导致又出现了超卖问题！
 * */

    /**
     * ID前缀
     */
    public static final String ID_PREFIX = UUID.randomUUID().toString(true);

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @param timeoutSec 超时时间
     */
    @Override
    public boolean tryLock(long timeoutSec) {

        String threadId = ID_PREFIX + Thread.currentThread().getId() + " ";
        Boolean success = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //判断锁的线程标识是否与当前线程一致
        String currentThreadFlag = ID_PREFIX + Thread.currentThread().getId();
        String redisThreadFlag = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (redisThreadFlag.equals(currentThreadFlag)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }
}
