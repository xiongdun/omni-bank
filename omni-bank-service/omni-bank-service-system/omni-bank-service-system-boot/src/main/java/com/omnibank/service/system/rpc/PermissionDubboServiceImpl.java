package com.omnibank.service.system.rpc;

import com.omnibank.service.system.api.rpc.PermissionDubboService;
import com.omnibank.service.system.service.role.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 权限 Dubbo 服务实现（triple 协议，注册于 system 服务名下）：供其他服务跨域鉴权
 */
@DubboService
@RequiredArgsConstructor
public class PermissionDubboServiceImpl implements PermissionDubboService {

    private final SysRoleService roleService;

    @Override
    public List<String> getRoleCodes(Long userId) {
        return roleService.getRoleCodes(userId);
    }

    @Override
    public List<String> getPermissions(Long userId) {
        return roleService.getPermissions(userId);
    }
}
