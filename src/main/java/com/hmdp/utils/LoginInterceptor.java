package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    //执行请求前
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse
            response, Object handler) throws Exception {
        //判断是否要拦截(ThreadLocal中是否有⽤户)
        if(UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        //放⾏
        return true;}

}
