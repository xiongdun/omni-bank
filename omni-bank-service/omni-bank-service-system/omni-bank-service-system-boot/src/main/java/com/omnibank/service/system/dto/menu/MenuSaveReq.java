package com.omnibank.service.system.dto.menu;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 菜单/权限点保存请求（id 为空=新增，非空=修改）
 */
@Data
public class MenuSaveReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单 ID（修改时必填） */
    private Long id;

    /** 父级 ID（0 为根） */
    private Long parentId = 0L;

    /** 菜单/权限点名称 */
    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    /** 类型（M 目录 C 菜单 F 按钮） */
    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    /** 权限标识（按钮类型必填，如 sys:user:add） */
    private String permission;

    /** 路由地址 */
    private String routePath;

    /** 前端组件路径 */
    private String component;

    /** 图标 */
    private String icon;

    /** 显示顺序 */
    private Integer sort = 0;

    /** 是否可见（0 显示 1 隐藏） */
    private String visible = "0";

    /** 状态（0 正常 1 停用） */
    private String status = "0";
}
