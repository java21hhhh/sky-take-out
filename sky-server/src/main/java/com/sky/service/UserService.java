package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;

public interface UserService {

    /**
     * 微信登录
     *
     * @param userLoginDTO 前端传过来的授权码 code
     * @return 用户对象（新用户首次登录会自动完成注册）
     */
    User wxLogin(UserLoginDTO userLoginDTO);

}
