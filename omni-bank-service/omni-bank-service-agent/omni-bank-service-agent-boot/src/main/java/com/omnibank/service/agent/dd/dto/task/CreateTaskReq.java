package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建尽调任务请求：草稿允许申请信息部分字段暂缺，提交时再做场景化校验
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateTaskReq extends BaseDTO {

    /** 企业主体 */
    @Valid
    @NotNull(message = "企业信息不能为空")
    private EnterpriseReq enterprise;

    /** 授信申请 */
    @Valid
    @NotNull(message = "授信申请信息不能为空")
    private LoanApplicationReq application;

    /** 任务名称（缺省按企业名称生成） */
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;
}
