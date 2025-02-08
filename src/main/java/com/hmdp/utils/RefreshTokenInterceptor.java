package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse
            response, Object handler) throws Exception {
//获取请求头中的token
        String token = request.getHeader("authorization");
//根据token获取redis中的⽤户
        if (StringUtils.isEmpty(token)) {
            return true;
        }
        Map<Object, Object> map =
                stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (map.isEmpty()) {
            return true;
        }
//保存⽤户信息到ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
//刷新token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL,
                TimeUnit.MINUTES);
//放⾏
        return true;
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse
            response, Object handler, Exception ex) throws Exception {
//移除ThreadLocal中的⽤户
        UserHolder.removeUser();
    }
}
