package com.omnibank.common.core.validation;

/**
 * Bean Validation 校验分组：新增/修改共用同一 DTO 时区分必填规则，
 * 用法：@NotNull(groups = {ValidGroup.Create.class, ValidGroup.Update.class})
 */
public interface ValidGroup {

    /** 新增场景 */
    interface Create {
    }

    /** 修改场景 */
    interface Update {
    }
}
