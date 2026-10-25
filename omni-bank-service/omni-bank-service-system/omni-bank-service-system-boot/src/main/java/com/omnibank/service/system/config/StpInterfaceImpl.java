package com.omnibank.service.system.config;

import cn.dev33.satoken.stp.StpInterface;
import com.omnibank.service.system.service.role.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源：@SaCheckRole / @SaCheckPermission 注解鉴权的角色与权限来源。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleService roleService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return roleService.getPermissions(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleService.getRoleCodes(Long.valueOf(loginId.toString()));
    }
}
