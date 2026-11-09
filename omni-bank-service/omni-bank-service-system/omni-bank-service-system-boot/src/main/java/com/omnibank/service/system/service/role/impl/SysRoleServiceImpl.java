package com.omnibank.service.system.service.role.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.role.RoleCreateReq;
import com.omnibank.service.system.dto.role.RoleQueryReq;
import com.omnibank.service.system.dto.role.RoleUpdateReq;
import com.omnibank.service.system.entity.SysMenu;
import com.omnibank.service.system.entity.SysRole;
import com.omnibank.service.system.entity.SysRoleMenu;
import com.omnibank.service.system.entity.SysUserRole;
import com.omnibank.service.system.mapper.SysRoleMapper;
import com.omnibank.service.system.service.role.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 角色服务实现（MyBatis-Plus ServiceImpl 体系）：
 * 主实体经 this.lambdaQuery()/save/updateById/removeById 链式操作；
 * 关联表（用户-角色/角色-菜单、菜单）经 Db 工具类批量操作。
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    /** 超级管理员角色编码：拥有全部权限点（通配） */
    private static final String SUPER_ADMIN_ROLE_CODE = "super_admin";

    /** 权限通配符 */
    private static final String PERMISSION_WILDCARD = "*:*:*";

    @Override
    public IPage<SysRole> page(RoleQueryReq req) {
        return lambdaQuery()
                .like(req.getRoleName() != null, SysRole::getRoleName, req.getRoleName())
                .eq(req.getStatus() != null, SysRole::getStatus, req.getStatus())
                .orderByAsc(SysRole::getSort)
                .page(new Page<>(req.getCurrent(), req.getSize()));
    }

    @Override
    public List<SysRole> listAll() {
        return lambdaQuery()
                .eq(SysRole::getStatus, "0")
                .orderByAsc(SysRole::getSort)
                .list();
    }

    @Override
    public SysRole getById(Long id) {
        SysRole role = super.getById(id);
        if (role == null) {
            throw new BizException(SysErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RoleCreateReq req) {
        if (lambdaQuery().eq(SysRole::getRoleCode, req.getRoleCode()).exists()) {
            throw new BizException(SysErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        role.setRoleName(req.getRoleName());
        role.setRoleCode(req.getRoleCode());
        role.setDataScope(req.getDataScope());
        role.setSort(req.getSort());
        role.setStatus(req.getStatus());
        role.setRemark(req.getRemark());
        save(role);
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
        updateById(role);
        if (req.getMenuIds() != null) {
            assignMenus(req.getId(), req.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        Long assignedUsers = Db.count(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, id));
        if (assignedUsers > 0) {
            throw new BizException(SysErrorCode.ROLE_ASSIGNED);
        }
        removeById(id);
        Db.remove(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        Db.remove(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            Db.saveBatch(menuIds.stream().map(menuId -> {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                return roleMenu;
            }).toList());
        }
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        List<Long> roleIds = listUserRoles(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, "0")
                .list()
                .stream()
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
        List<Long> roleIds = listUserRoles(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = Db.list(Wrappers.<SysRoleMenu>lambdaQuery()
                        .in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return Db.list(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getId, menuIds)).stream()
                .map(SysMenu::getPermission)
                .filter(permission -> permission != null && !permission.isBlank())
                .distinct()
                .toList();
    }

    /** 查询用户-角色关联的角色 ID 列表 */
    private List<Long> listUserRoles(Long userId) {
        return Db.list(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }
}
