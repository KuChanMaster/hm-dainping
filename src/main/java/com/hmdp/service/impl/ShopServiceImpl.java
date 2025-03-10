package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopMapper shopMapper;

    //线程池
    private static final ExecutorService THREADPOOL = Executors.newFixedThreadPool(10);

    //开启新线程往redis写入带有逻辑过期的数据。
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id); 
        try {
            TimeUnit.MICROSECONDS.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    //逻辑过期解决缓存击穿问题
    @Override
    public Result queryById(Long id) {
        //查询redis,若存在则转化对象后返回
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //未命中则说明不是热点key（也即不是活动商品）
        if (StringUtils.isBlank(shopJson)) {
            return null;
        }
        //判断是够过期，对于拥有泛型的特殊泛华，然后可以进行强制转换
        RedisData redisData = JSONUtil.toBean(shopJson, new TypeReference<RedisData<Shop>>() {
        }, false);
        Shop shop = (Shop) redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return Result.ok(shop);
        }
        //实现缓存重建，解决缓存击穿问题(通过加互斥锁)
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            shopJson = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(shopJson, new TypeReference<RedisData<Shop>>() {
            }, false);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return Result.ok((Shop) redisData.getData());
            }
            THREADPOOL.submit(() -> {
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    stringRedisTemplate.delete(lockKey);
                }
            });
        }
        return Result.ok(shop);
    }
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(
                        key,
                        "1",
                        LOCK_SHOP_TTL,
                        TimeUnit.SECONDS);
        return false;
    }
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
    @Override
    @Transactional//保障原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在");
        }
        //先更新数据库后删除缓存
        shopMapper.updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
