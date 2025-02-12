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

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopMapper shopMapper;

    @Override
    public Result queryById(Long id) {
        //查询redis,若存在则转化对象后返回
        String key=CACHE_SHOP_KEY+id;
        String shopJson=stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isBlank(shopJson)){
            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        Shop shop=shopMapper.selectById(id);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Override
    @Transactional//保障原子性
    public Result update(Shop shop) {
        Long id= shop.getId();
        if (id==null){
            return Result.fail("店铺不存在");
        }
        //先更新数据库后删除缓存
        shopMapper.updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();

    }
}
