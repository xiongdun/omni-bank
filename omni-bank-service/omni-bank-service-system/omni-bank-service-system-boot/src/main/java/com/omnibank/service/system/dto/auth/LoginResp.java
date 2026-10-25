package com.omnibank.service.system.dto.auth;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录响应：管理端将 token 放入 Header（Authorization）携带访问
 */
@Data
public class LoginResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** token 名称（Header 键） */
    private String tokenName;

    /** token 值 */
    private String tokenValue;
}
