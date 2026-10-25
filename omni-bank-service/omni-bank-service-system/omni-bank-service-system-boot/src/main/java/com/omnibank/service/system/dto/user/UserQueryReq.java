package com.omnibank.service.system.dto.user;

import com.omnibank.service.system.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryReq extends PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号（模糊） */
    private String username;

    /** 用户昵称（模糊） */
    private String nickname;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 所属机构/部门 ID（精确） */
    private Long orgId;
}
