package com.omnibank.service.system.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统管理域错误码：号段 220001-229999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum SysErrorCode implements ErrorCode {

    USER_NOT_FOUND("220001", "用户不存在"),
    USER_ALREADY_EXISTS("220002", "登录账号已存在"),
    PASSWORD_ERROR("220003", "账号或密码错误"),
    USER_DISABLED("220004", "账号已停用"),
    ROLE_NOT_FOUND("220005", "角色不存在"),
    ROLE_CODE_EXISTS("220006", "角色编码已存在"),
    MENU_NOT_FOUND("220007", "菜单不存在"),
    MENU_HAS_CHILDREN("220008", "存在下级菜单，不允许删除"),
    ORG_NOT_FOUND("220009", "机构/部门不存在"),
    ORG_HAS_CHILDREN("220010", "存在下级机构/部门，不允许删除"),
    ROLE_ASSIGNED("220011", "角色已分配用户，不允许删除"),
    ORG_NOT_ALLOWED_DELETE("220012", "机构/部门下存在用户，不允许删除");

    private final String code;

    private final String msg;
}
