package com.omnibank.service.system.config;

import cn.dev33.satoken.stp.StpUtil;
import com.omnibank.common.core.context.CurrentUser;
import com.omnibank.common.core.context.UserContext;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.service.user.SysUserService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户上下文过滤器：解析 Sa-Token 登录态写入 UserContext（TTL 实现），
 * 供审计填充（common-mybatis）、异步线程池（TTL 透传）、BaseController 统一读取；
 * 请求结束统一清理，防线程复用串号。
 */
@Component
@RequiredArgsConstructor
public class UserContextFilter implements Filter {

    private final SysUserService userService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            fillContext();
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /** 已登录则加载用户写入上下文；未登录（登录接口/匿名接口）保持空，不做拦截（拦截由 SaInterceptor 负责） */
    private void fillContext() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return;
        }
        SysUser user = userService.getById(Long.valueOf(loginId.toString()));
        if (user == null) {
            return;
        }
        UserContext.set(CurrentUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .orgId(user.getOrgId())
                .build());
    }
}
