package com.omnibank.service.system.api.dto;

import com.omnibank.common.core.dto.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户信息 DTO（跨服务传输）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDTO extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 所属机构/部门 ID */
    private Long orgId;

    /** 状态：0 启用 1 停用 */
    private String status;
}
