package com.omnibank.common.core.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户信息（请求级上下文载体，经 {@link UserContext} 传递）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 登录账号（审计填充使用） */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 所属机构/部门 ID */
    private Long orgId;
}
