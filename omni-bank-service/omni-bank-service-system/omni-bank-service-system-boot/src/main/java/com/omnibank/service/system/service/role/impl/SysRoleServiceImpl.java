package com.omnibank.service.system.service.role.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.role.RoleCreateReq;
import com.omnibank.service.system.dto.role.RoleQueryReq;
import com.omnibank.service.system.dto.role.RoleUpdateReq;
import com.omnibank.service.system.entity.SysMenu;
import com.omnibank.service.system.entity.SysRole;
import com.omnibank.service.system.entity.SysRoleMenu;
import com.omnibank.service.system.entity.SysUserRole;
import com.omnibank.service.system.mapper.SysMenuMapper;
import com.omnibank.service.system.mapper.SysRoleMapper;
import com.omnibank.service.system.mapper.SysRoleMenuMapper;
import com.omnibank.service.system.mapper.SysUserRoleMapper;
import com.omnibank.service.system.service.role.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 角色服务实现：查重/菜单授权/用户权限查询（super_admin 通配全部权限）。
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    /** 超级管理员角色编码：拥有全部权限点（通配） */
    private static final String SUPER_ADMIN_ROLE_CODE = "super_admin";

    /** 权限通配符 */
    private static final String PERMISSION_WILDCARD = "*:*:*";

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public IPage<SysRole> page(RoleQueryReq req) {
        return roleMapper.selectPage(new Page<>(req.getCurrent(), req.getSize()),
                Wrappers.<SysRole>lambdaQuery()
                        .like(req.getRoleName() != null, SysRole::getRoleName, req.getRoleName())
                        .eq(req.getStatus() != null, SysRole::getStatus, req.getStatus())
                        .orderByAsc(SysRole::getSort));
    }

    @Override
    public List<SysRole> listAll() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getStatus, "0")
                .orderByAsc(SysRole::getSort));
    }

    @Override
    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(SysErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RoleCreateReq req) {
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, req.getRoleCode()));
        if (count > 0) {
            throw new BizException(SysErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        role.setRoleName(req.getRoleName());
        role.setRoleCode(req.getRoleCode());
        role.setDataScope(req.getDataScope());
        role.setSort(req.getSort());
        role.setStatus(req.getStatus());
        role.setRemark(req.getRemark());
        roleMapper.insert(role);
        if (req.getMenuIds() != null) {
            assignMenus(role.getId(), req.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleUpdateReq req) {
        getById(req.getId());
        SysRole role = new SysRole();
        role.setId(req.getId());
        role.setRoleName(req.getRoleName());
        role.setDataScope(req.getDataScope());
        role.setSort(req.getSort());
        role.setStatus(req.getStatus());
        role.setRemark(req.getRemark());
        roleMapper.updateById(role);
        if (req.getMenuIds() != null) {
            assignMenus(req.getId(), req.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        Long assignedUsers = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, id));
        if (assignedUsers > 0) {
            throw new BizException(SysErrorCode.ROLE_ASSIGNED);
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            menuIds.forEach(menuId -> {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenuMapper.insert(roleMenu);
            });
        }
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> "0".equals(role.getStatus()))
                .map(SysRole::getRoleCode)
                .toList();
    }

    @Override
    public List<String> getPermissions(Long userId) {
        List<String> roleCodes = getRoleCodes(userId);
        // 超级管理员通配全部权限点
        if (roleCodes.contains(SUPER_ADMIN_ROLE_CODE)) {
            return List.of(PERMISSION_WILDCARD);
        }
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                        .in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return menuMapper.selectBatchIds(menuIds).stream()
                .map(SysMenu::getPermission)
                .filter(permission -> permission != null && !permission.isBlank())
                .distinct()
                .toList();
    }
}
