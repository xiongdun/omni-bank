package com.omnibank.service.system.service.menu.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.common.core.result.GlobalErrorCode;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.menu.MenuSaveReq;
import com.omnibank.service.system.entity.SysMenu;
import com.omnibank.service.system.mapper.SysMenuMapper;
import com.omnibank.service.system.service.menu.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 菜单/权限点服务实现。
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    /** 按钮类型 */
    private static final String MENU_TYPE_BUTTON = "F";

    private final SysMenuMapper menuMapper;

    @Override
    public List<SysMenu> list() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSort));
    }

    @Override
    public List<SysMenu> listByParentId(Long parentId) {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, parentId)
                .orderByAsc(SysMenu::getSort));
    }

    @Override
    public void save(MenuSaveReq req) {
        // 按钮类型必须携带权限标识
        if (MENU_TYPE_BUTTON.equals(req.getMenuType())
                && (req.getPermission() == null || req.getPermission().isBlank())) {
            throw new BizException(GlobalErrorCode.PARAM_ERROR, "按钮类型的菜单必须配置权限标识");
        }
        SysMenu menu = new SysMenu();
        menu.setId(req.getId());
        menu.setParentId(req.getParentId());
        menu.setMenuName(req.getMenuName());
        menu.setMenuType(req.getMenuType());
        menu.setPermission(req.getPermission());
        menu.setRoutePath(req.getRoutePath());
        menu.setComponent(req.getComponent());
        menu.setIcon(req.getIcon());
        menu.setSort(req.getSort());
        menu.setVisible(req.getVisible());
        menu.setStatus(req.getStatus());
        if (menu.getId() == null) {
            menuMapper.insert(menu);
        } else {
            if (menuMapper.selectById(menu.getId()) == null) {
                throw new BizException(SysErrorCode.MENU_NOT_FOUND);
            }
            menuMapper.updateById(menu);
        }
    }

    @Override
    public void delete(Long id) {
        Long children = menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, id));
        if (children > 0) {
            throw new BizException(SysErrorCode.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
    }
}
