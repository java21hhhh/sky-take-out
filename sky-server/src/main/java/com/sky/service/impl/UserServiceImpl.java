package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户端微信登录服务
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    //微信服务端获取会话（openid/session_key）的地址
    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

    /**
     * 微信登录：
     * 1. 用前端传来的授权码 code 调用微信接口换取 openid
     * 2. 若 openid 为空说明 code 无效，登录失败
     * 3. 按 openid 查询用户，不存在则自动注册（新用户）
     *
     * @param userLoginDTO 前端传过来的授权码
     * @return 用户对象
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String code = userLoginDTO.getCode();
        log.info("微信用户登录，code: {}", code);

        //1. 调用微信接口，使用当前 code 换取 openid（session_key）
        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN_URL, map);

        //2. 解析响应，若 openid 为空说明 code 无效（或小程序配置错误），登录失败
        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject == null ? null : jsonObject.getString("openid");
        log.info("微信返回 openid: {}", openid);
        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //3. 判断当前用户是否为新用户
        User user = userMapper.getByOpenid(openid);

        //4. 新用户则自动完成注册
        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            log.info("新用户自动注册成功，id: {}", user.getId());
        }
        return user;
    }
}
