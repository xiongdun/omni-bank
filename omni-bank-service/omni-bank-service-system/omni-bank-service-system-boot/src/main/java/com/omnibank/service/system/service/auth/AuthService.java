package com.omnibank.service.system.service.auth;

import com.omnibank.service.system.dto.auth.LoginReq;
import com.omnibank.service.system.dto.auth.LoginResp;

/**
 * 认证服务：登录/登出/当前用户
 */
public interface AuthService {

    /** 账号密码登录，签发 token */
    LoginResp login(LoginReq req);

    /** 登出（注销当前会话） */
    void logout();

    /** 获取当前登录用户 ID */
    long getCurrentUserId();
}
