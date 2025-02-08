package com.hmdp.utils;

import cn.hutool.jwt.JWT;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtHelper {
    private static long tokenExpiration = 24 * 60 * 1000;//token有效时间

    private static String tokenSignKey = "ohj";

    public static String createToken(Long id) {
        String token = JWT.create().setPayload("id", id)
                .setExpiresAt(new Date(System.currentTimeMillis() + tokenExpiration))
                .setKey(tokenSignKey.getBytes()).sign();
        return token;
    }
    public static Long getId(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        Long id = (Long) JWT.of(token).getPayload("id");
        return id;
    }
}
