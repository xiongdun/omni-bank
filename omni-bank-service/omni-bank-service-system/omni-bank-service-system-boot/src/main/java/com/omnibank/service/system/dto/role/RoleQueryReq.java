package com.omnibank.service.system.dto.role;

import com.omnibank.service.system.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQueryReq extends PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称（模糊） */
    private String roleName;

    /** 状态（0 正常 1 停用） */
    private String status;
}
