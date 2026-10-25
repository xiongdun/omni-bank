package com.omnibank.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 审计字段自动填充：insert 填充 createTime/creator/updateTime/updater，update 填充 updateTime/updater
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /** TODO: 接入用户上下文后替换为当前登录用户标识 */
    private static final String DEFAULT_OPERATOR = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "creator", String.class, DEFAULT_OPERATOR);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updater", String.class, DEFAULT_OPERATOR);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updater", String.class, DEFAULT_OPERATOR);
    }
}
