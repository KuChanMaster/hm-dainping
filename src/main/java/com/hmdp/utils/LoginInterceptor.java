package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    //执行请求前
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取session
        HttpSession session=request.getSession();
        //2.获取session中的用户
        Object user=session.getAttribute("user");
        //3判断用户是否存在
        if (user==null){
            response.setStatus(401);//返回401（未授权状态）
            return false;
        }
        //4存在保存用户到ThreadLocal

        UserHolder.saveUser((UserDTO)user);
        //5放行
        return true;
    }
    //页面渲染后
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        UserHolder.removeUser();
    }
}
