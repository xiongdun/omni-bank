package com.omnibank.service.system.api.rpc;

import com.omnibank.service.system.api.dto.UserDTO;

/**
 * 用户 Dubbo 服务接口（triple 协议）：其他服务跨域查询用户信息
 */
public interface UserDubboService {

    /** 按用户 ID 查询用户信息 */
    UserDTO getUser(Long userId);
}
