package com.omnibank.service.system.dto.org;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 机构/部门保存请求（id 为空=新增，非空=修改）
 */
@Data
public class OrgSaveReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 机构/部门 ID（修改时必填） */
    private Long id;

    /** 父级 ID（0 为根） */
    private Long parentId = 0L;

    /** 名称 */
    @NotBlank(message = "名称不能为空")
    private String orgName;

    /** 类型（1 机构 2 部门） */
    @NotBlank(message = "类型不能为空")
    private String orgType;

    /** 显示顺序 */
    private Integer sort = 0;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0 正常 1 停用） */
    private String status = "0";
}
