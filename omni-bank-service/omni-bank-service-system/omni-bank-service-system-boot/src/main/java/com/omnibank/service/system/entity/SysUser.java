package com.omnibank.service.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 所属机构/部门 ID */
    private Long orgId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 密码（BCrypt）：序列化时忽略，仅反序列化（创建/改密入参）可写 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 用户类型（1 系统用户 2 业务用户） */
    private String userType;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 备注 */
    private String remark;
}
