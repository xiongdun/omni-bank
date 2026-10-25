package com.omnibank.service.system.dto.role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色新增请求
 */
@Data
public class RoleCreateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    /** 角色编码 */
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    /** 数据权限（1 全部 2 自定义 3 本机构 4 本部门及以下 5 仅本人） */
    private String dataScope = "1";

    /** 显示顺序 */
    private Integer sort = 0;

    /** 状态（0 正常 1 停用） */
    private String status = "0";

    /** 备注 */
    private String remark;

    /** 菜单/权限点 ID 列表 */
    private List<Long> menuIds;
}
