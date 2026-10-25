package com.omnibank.service.system.service.menu;

import com.omnibank.service.system.dto.menu.MenuSaveReq;
import com.omnibank.service.system.entity.SysMenu;

import java.util.List;

/**
 * 菜单/权限点服务：树查询/增删改
 */
public interface SysMenuService {

    /** 全量菜单树（管理端维护用，平铺由前端组树亦可，此处直接返回平铺列表） */
    List<SysMenu> list();

    /** 按父级查询子菜单 */
    List<SysMenu> listByParentId(Long parentId);

    /** 保存菜单（id 空=新增，非空=修改） */
    void save(MenuSaveReq req);

    /** 删除菜单（存在下级时拒绝） */
    void delete(Long id);
}
