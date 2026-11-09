package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业主体入参：优先统一社会信用代码，兼容行内客户编号
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseReq extends BaseDTO {

    /** 统一社会信用代码 */
    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(max = 32, message = "统一社会信用代码长度不能超过 32")
    private String creditCode;

    /** 银行企业客户编号 */
    @Size(max = 64, message = "客户编号长度不能超过 64")
    private String customerId;

    /** 企业名称：主体首次入库时必填，已有主体可缺省 */
    @Size(max = 256, message = "企业名称长度不能超过 256")
    private String enterpriseName;
}
