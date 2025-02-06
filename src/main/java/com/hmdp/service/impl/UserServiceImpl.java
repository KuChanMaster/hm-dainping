package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
           //2.手机号不合法
            return Result.fail("手机格式错误");
        }
        //3。生成验证
        String code= RandomUtil.randomNumbers(6);
        //4.将验证码传入Session
        session.setAttribute("code",code);
        //5.发送验证码
        log.debug("发送短信验证码： {}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone=loginForm.getPhone();
        String code=loginForm.getCode();
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.验证码校验
        Object cacheCode=session.getAttribute("code");
        if(cacheCode==null||!code.equals(cacheCode.toString())){
            return Result.fail("验证码错误");
        }
        //查询数据库

        LambdaQueryWrapper<User> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.isBlank(phone),User::getPhone,phone);
        User user=query().eq("phone",phone).one();
        if(user==null){
            user=createUserWithPhone(phone);
        }
        //保存用户信息到session中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
    }
    private User createUserWithPhone(String phone){
        User user=new User();
        user.setPhone(phone).setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(6));
        userMapper.insert(user);
        return user;
    }
}
