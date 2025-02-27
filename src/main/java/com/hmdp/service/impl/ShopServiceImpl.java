package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopMapper shopMapper;
    @Override
    public Result queryById(Long id) {
        //查询redis,若存在则转化对象后返回
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //这⾥判断的时shopJson是否真的有值，不包括空值
        if (StringUtils.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
//判断命中的是否是空值,解决缓存穿透
//解释： shopJson是空值的时候就是命中缓存了，只有为null时才查询数据库
        if (shopJson != null) {
            return Result.fail("店铺信息不存在");
        }
        //实现缓存重建，解决缓存击穿问题(通过加互斥锁)
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            boolean lock = tryLock(lockKey);
            while (!lock) {
                //获取锁失败则休眠一下，然后重新获取
                TimeUnit.MICROSECONDS.sleep(50);
                lock = tryLock(lockKey);
            }
            //DouleCheck(此时有可能别的线程已经重新构建好缓存)
            shopJson = stringRedisTemplate.opsForValue().get(key);
            //判断的是shopJson是否有真值，不包括空值
            if (StringUtils.isNotBlank(shopJson)) {
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(shop);
            }
            try {
                TimeUnit.MICROSECONDS.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            shop = shopMapper.selectById(id);
            if (shop == null) {
                //将空值写入redis，解决穿透问题
                stringRedisTemplate.opsForValue().set(
                        key,
                        "",
                        CACHE_NULL_TTL,
                        TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            stringRedisTemplate.opsForValue().set(key,
                    JSONUtil.toJsonStr(shop),
                    CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(key);
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
