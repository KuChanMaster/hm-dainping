package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import com.hmdp.utils.JwtHelper;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.手机号不合法
            return Result.fail("手机格式错误");
        }
        //3。生成验证
        String code = RandomUtil.randomNumbers(6);
//        //4.将验证码传入Session
//        session.setAttribute("code",code);
        //4.将验证码发到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        log.debug("发送短信验证码： {}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        /**
         * 2.session验证码校验
         Object cacheCode=session.getAttribute("code");
         if(cacheCode==null||!code.equals(cacheCode.toString())){
         return Result.fail("验证码错误");
         }*/
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !code.equals(cacheCode)) {
            return Result.fail("验证码错误");
        }
        //查询数据库

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.isBlank(phone), User::getPhone, phone);
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        /**保存用户信息到session中
         session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/
        //生成token
        String token = JwtHelper.createToken(user.getId());
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue)-> fieldValue.toString()));
        /**
         * 这部分代码将UserDTO对象转换为一个Map。其中：
         * setIgnoreNullValue(true) 表示忽略值为null的属性。
         * setFieldValueEditor 提供了一个编辑器，可以对字段值进行自定义处理，在这个例子中，是将所有字段值转换为字符串形式。
         * */
        String tokenkey=LOGIN_USER_KEY+token;
        //存储token
        stringRedisTemplate.opsForHash().putAll(tokenkey,map);
        //设置token有效期
        stringRedisTemplate.expire(tokenkey,LOGIN_USER_TTL,TimeUnit.SECONDS);
        return Result.ok(token);

    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone).setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        userMapper.insert(user);
        return user;
    }
}
