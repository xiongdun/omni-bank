package com.omnibank.service.system.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户修改请求（不含密码，改密走重置接口）
 */
@Data
public class UserUpdateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    /** 用户昵称 */
    private String nickname;

    /** 所属机构/部门 ID */
    private Long orgId;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 备注 */
    private String remark;

    /** 角色 ID 列表（全量覆盖） */
    private List<Long> roleIds;

    /** 岗位 ID 列表（全量覆盖） */
    private List<Long> postIds;
}
