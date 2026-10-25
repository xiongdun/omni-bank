package com.omnibank.service.system.dto.role;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色修改请求
 */
@Data
public class RoleUpdateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    @NotNull(message = "角色 ID 不能为空")
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 数据权限（1 全部 2 自定义 3 本机构 4 本部门及以下 5 仅本人） */
    private String dataScope;

    /** 显示顺序 */
    private Integer sort;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 备注 */
    private String remark;

    /** 菜单/权限点 ID 列表（全量覆盖，非空时覆盖授权） */
    private List<Long> menuIds;
}
