package com.omnibank.service.system.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求
 */
@Data
public class LoginReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    @NotBlank(message = "登录账号不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
