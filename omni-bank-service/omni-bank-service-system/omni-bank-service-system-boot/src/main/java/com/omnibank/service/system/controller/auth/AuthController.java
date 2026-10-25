package com.omnibank.service.system.controller.auth;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.omnibank.common.core.result.Result;
import com.omnibank.service.system.dto.auth.LoginReq;
import com.omnibank.service.system.dto.auth.LoginResp;
import com.omnibank.service.system.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录/登出/当前用户
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 账号密码登录（放行接口，返回 token，管理端放入 Header Authorization） */
    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.success(authService.login(req));
    }

    /** 登出（注销当前会话） */
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /** 当前登录用户 ID */
    @SaCheckLogin
    @GetMapping("/me")
    public Result<Long> me() {
        return Result.success(authService.getCurrentUserId());
    }
}
