package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
    @Override
    public Result queryById(Long id) {
        //查询redis,若存在则转化对象后返回
        String key=CACHE_SHOP_KEY+id;
        String shopJson=stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isBlank(shopJson)){
            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        return null;
    }
}
