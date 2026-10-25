package com.omnibank.service.system.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户新增请求
 */
@Data
public class UserCreateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    @NotBlank(message = "登录账号不能为空")
    private String username;

    /** 用户昵称 */
    @NotBlank(message = "用户昵称不能为空")
    private String nickname;

    /** 初始密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 所属机构/部门 ID */
    @NotNull(message = "所属机构/部门不能为空")
    private Long orgId;

    /** 用户类型（1 系统用户 2 业务用户），缺省业务用户 */
    private String userType = "2";

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0 正常 1 停用） */
    private String status = "0";

    /** 备注 */
    private String remark;

    /** 角色 ID 列表 */
    private List<Long> roleIds;

    /** 岗位 ID 列表 */
    private List<Long> postIds;
}
