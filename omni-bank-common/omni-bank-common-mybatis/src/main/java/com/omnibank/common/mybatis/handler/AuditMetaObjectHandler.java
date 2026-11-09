package com.omnibank.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.omnibank.common.core.context.UserContext;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 审计字段自动填充：insert 填充 createTime/creator/updateTime/updater，update 填充 updateTime/updater。
 * 操作人从 {@link UserContext} 取当前登录账号，未登录（系统任务/未接入上下文）时回落 system。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String DEFAULT_OPERATOR = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "creator", String.class, operator);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updater", String.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updater", String.class, currentOperator());
    }

    /** 当前操作人：用户上下文优先，缺失回落 system */
    private String currentOperator() {
        String username = UserContext.getUsername();
        return username == null || username.isBlank() ? DEFAULT_OPERATOR : username;
    }
}
