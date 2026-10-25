package com.omnibank.service.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单/权限点（menu_type：M 目录 C 菜单 F 按钮）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 父级 ID（0 为根） */
    private Long parentId;

    /** 菜单/权限点名称 */
    private String menuName;

    /** 类型（M 目录 C 菜单 F 按钮） */
    private String menuType;

    /** 权限标识（如 sys:user:add，按钮类型必填） */
    private String permission;

    /** 路由地址 */
    private String routePath;

    /** 前端组件路径 */
    private String component;

    /** 图标 */
    private String icon;

    /** 显示顺序 */
    private Integer sort;

    /** 是否可见（0 显示 1 隐藏） */
    private String visible;

    /** 状态（0 正常 1 停用） */
    private String status;
}
