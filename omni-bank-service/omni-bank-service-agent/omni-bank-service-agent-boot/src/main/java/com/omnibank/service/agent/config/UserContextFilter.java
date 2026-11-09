package com.omnibank.service.agent.config;

import com.omnibank.common.core.context.CurrentUser;
import com.omnibank.common.core.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 轻量用户上下文过滤器：第一期不建设登录与权限体系，仅解析网关注入的用户标识用于审计。
 * 未携带用户标识时不写入上下文，审计字段回落 system。
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";

    private static final String HEADER_USERNAME = "X-Username";

    private static final String HEADER_NICKNAME = "X-Nickname";

    private static final String HEADER_ORG_ID = "X-Org-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = request.getHeader(HEADER_USERNAME);
        if (StringUtils.hasText(username)) {
            UserContext.set(CurrentUser.builder()
                    .userId(parseLong(request.getHeader(HEADER_USER_ID)))
                    .username(username)
                    .nickname(request.getHeader(HEADER_NICKNAME))
                    .orgId(parseLong(request.getHeader(HEADER_ORG_ID)))
                    .build());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
