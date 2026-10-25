package com.omnibank.service.system.api.rpc;

import java.util.List;

/**
 * 权限 Dubbo 服务接口（triple 协议）：其他服务跨域校验用户角色与权限
 */
public interface PermissionDubboService {

    /** 查询用户角色编码列表（如 super_admin） */
    List<String> getRoleCodes(Long userId);

    /** 查询用户权限点列表（如 sys:user:add） */
    List<String> getPermissions(Long userId);
}
