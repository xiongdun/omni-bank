package com.omnibank.service.system.rpc;

import com.omnibank.service.system.api.dto.UserDTO;
import com.omnibank.service.system.api.rpc.UserDubboService;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.service.user.SysUserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 用户 Dubbo 服务实现（triple 协议，注册于 system 服务名下）
 */
@DubboService
@RequiredArgsConstructor
public class UserDubboServiceImpl implements UserDubboService {

    private final SysUserService userService;

    @Override
    public UserDTO getUser(Long userId) {
        SysUser user = userService.getById(userId);
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setOrgId(user.getOrgId());
        dto.setStatus(user.getStatus());
        return dto;
    }
}
