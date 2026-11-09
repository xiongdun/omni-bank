package com.omnibank.common.web.base;

import com.omnibank.common.core.context.CurrentUser;
import com.omnibank.common.core.context.UserContext;

/**
 * Controller 基类：提供当前登录操作人等公共能力，按需继承。
 * 用户上下文由各服务的认证过滤器（如 system 服务的 UserContextFilter）写入，
 * 本类不依赖具体认证框架（Sa-Token 等），保持 common-web 中立。
 */
public abstract class BaseController {

    /** 当前操作人账号，未登录时回落 system（与审计填充 AuditMetaObjectHandler 策略一致） */
    protected String getOperator() {
        String username = UserContext.getUsername();
        return username == null || username.isBlank() ? "system" : username;
    }

    /** 当前登录用户完整信息，未登录返回 null */
    protected CurrentUser getCurrentUser() {
        return UserContext.get();
    }
}
