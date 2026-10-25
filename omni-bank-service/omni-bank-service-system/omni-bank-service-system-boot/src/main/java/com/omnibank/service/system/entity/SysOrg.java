package com.omnibank.service.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机构/部门（单表树，org_type 区分机构与部门）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org")
public class SysOrg extends BaseEntity {

    /** 父级 ID（0 为根） */
    private Long parentId;

    /** 祖级路径（逗号分隔，含自身上级） */
    private String ancestors;

    /** 名称 */
    private String orgName;

    /** 类型（1 机构 2 部门） */
    private String orgType;

    /** 显示顺序 */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0 正常 1 停用） */
    private String status;
}
