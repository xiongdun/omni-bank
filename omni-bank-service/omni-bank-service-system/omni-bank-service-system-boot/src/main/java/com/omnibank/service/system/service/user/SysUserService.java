package com.omnibank.service.system.service.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.system.dto.user.UserCreateReq;
import com.omnibank.service.system.dto.user.UserQueryReq;
import com.omnibank.service.system.dto.user.UserUpdateReq;
import com.omnibank.service.system.entity.SysUser;

/**
 * 用户服务：分页/增删改/角色岗位分配/重置密码
 */
public interface SysUserService {

    /** 分页查询用户 */
    IPage<SysUser> page(UserQueryReq req);

    /** 按 ID 查询用户 */
    SysUser getById(Long id);

    /** 按登录账号查询用户 */
    SysUser getByUsername(String username);

    /** 新增用户（含角色/岗位分配） */
    void create(UserCreateReq req);

    /** 修改用户（含角色/岗位重分配） */
    void update(UserUpdateReq req);

    /** 删除用户（逻辑删除） */
    void delete(Long id);

    /** 重置密码（初始密码取系统设置 sys.user.init.password） */
    void resetPassword(Long id);

    /** 全量覆盖用户角色 */
    void assignRoles(Long userId, java.util.List<Long> roleIds);
}
