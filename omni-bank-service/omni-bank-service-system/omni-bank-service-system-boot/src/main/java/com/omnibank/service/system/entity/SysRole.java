package com.omnibank.service.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称 */
    private String roleName;

    /** 角色编码（如 super_admin） */
    private String roleCode;

    /** 数据权限（1 全部 2 自定义 3 本机构 4 本部门及以下 5 仅本人） */
    private String dataScope;

    /** 显示顺序 */
    private Integer sort;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 备注 */
    private String remark;
}
