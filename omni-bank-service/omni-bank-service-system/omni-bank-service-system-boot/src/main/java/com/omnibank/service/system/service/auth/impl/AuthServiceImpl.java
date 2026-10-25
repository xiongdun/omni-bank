package com.omnibank.service.system.service.auth.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.auth.LoginReq;
import com.omnibank.service.system.dto.auth.LoginResp;
import com.omnibank.service.system.entity.SysLoginLog;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.mapper.SysLoginLogMapper;
import com.omnibank.service.system.mapper.SysUserMapper;
import com.omnibank.service.system.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现：BCrypt 密码校验 + Sa-Token 会话签发 + 登录日志留痕。
 * TODO: 登录日志落库 P2 改经统一业务线程池异步执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResp login(LoginReq req) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername()));
        // 账号不存在与密码错误统一提示，防止账号枚举
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            saveLoginLog(req.getUsername(), "1", "账号或密码错误");
            throw new BizException(SysErrorCode.PASSWORD_ERROR);
        }
        if ("1".equals(user.getStatus())) {
            saveLoginLog(req.getUsername(), "1", "账号已停用");
            throw new BizException(SysErrorCode.USER_DISABLED);
        }
        StpUtil.login(user.getId());
        saveLoginLog(req.getUsername(), "0", "登录成功");
        LoginResp resp = new LoginResp();
        resp.setTokenName(StpUtil.getTokenName());
        resp.setTokenValue(StpUtil.getTokenValue());
        return resp;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public long getCurrentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 登录日志落库（IP/浏览器等上下文信息 P2 补充采集） */
    private void saveLoginLog(String username, String status, String msg) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUsername(username);
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            // 日志失败不阻断登录
            log.error("登录日志写入失败: username={}", username, e);
        }
    }
}
