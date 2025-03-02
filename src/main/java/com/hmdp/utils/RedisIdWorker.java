package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class RedisIdWorker {
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static final long BEGIN_TIMESTAMP = 1740873600L;
    //序列号的位数
    private static final int COUNT_BITS = 32;

    public long createId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long secondForNow = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = secondForNow - BEGIN_TIMESTAMP;
        //生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate
                .opsForValue()
                .increment("icr:" + keyPrefix + ":" + date);

        //拼接并返回
        return timestamp << COUNT_BITS | count;

    }

    public void createTimestamp(){
        //生成2024-1-1对应的秒数
        LocalDateTime time=LocalDateTime.of(2024,1,1,0,0);
        long second=time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }

}
