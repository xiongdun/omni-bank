package com.omnibank.service.system.service.role;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.system.dto.role.RoleCreateReq;
import com.omnibank.service.system.dto.role.RoleQueryReq;
import com.omnibank.service.system.dto.role.RoleUpdateReq;
import com.omnibank.service.system.entity.SysRole;

import java.util.List;

/**
 * 角色服务：分页/增删改/菜单授权/用户权限查询
 */
public interface SysRoleService {

    /** 分页查询角色 */
    IPage<SysRole> page(RoleQueryReq req);

    /** 查询全部启用角色（下拉用） */
    List<SysRole> listAll();

    /** 按 ID 查询角色 */
    SysRole getById(Long id);

    /** 新增角色（含菜单授权） */
    void create(RoleCreateReq req);

    /** 修改角色（menuIds 非空时覆盖授权） */
    void update(RoleUpdateReq req);

    /** 删除角色（已分配用户时拒绝） */
    void delete(Long id);

    /** 全量覆盖角色-菜单授权 */
    void assignMenus(Long roleId, List<Long> menuIds);

    /** 查询用户角色编码列表（跨服务鉴权契约） */
    List<String> getRoleCodes(Long userId);

    /** 查询用户权限点列表（跨服务鉴权契约；super_admin 返回 "*:*:*" 通配） */
    List<String> getPermissions(Long userId);
}
