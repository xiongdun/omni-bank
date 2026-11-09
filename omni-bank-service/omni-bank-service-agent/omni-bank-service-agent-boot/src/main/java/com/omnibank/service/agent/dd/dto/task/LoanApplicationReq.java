package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 授信申请入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoanApplicationReq extends BaseDTO {

    /** 原信贷系统申请编号 */
    @Size(max = 64, message = "申请编号长度不能超过 64")
    private String applicationNo;

    /** 原始申请业务类型 */
    @Size(max = 64, message = "业务类型长度不能超过 64")
    private String applicationType;

    /** 产品编码 */
    @Size(max = 64, message = "产品编码长度不能超过 64")
    private String productCode;

    /** 产品名称 */
    @Size(max = 128, message = "产品名称长度不能超过 128")
    private String productName;

    /** 申请金额 */
    @NotNull(message = "申请金额不能为空")
    @DecimalMin(value = "0.01", message = "申请金额必须大于 0")
    private BigDecimal applyAmount;

    /** 币种 */
    @Size(max = 8, message = "币种长度不能超过 8")
    private String currency = "CNY";

    /** 申请期限 */
    @NotNull(message = "申请期限不能为空")
    @Min(value = 1, message = "申请期限必须大于 0")
    private Integer applyTerm;

    /** 期限单位 */
    @Size(max = 16, message = "期限单位长度不能超过 16")
    private String termUnit;

    /** 贷款用途 */
    @Size(max = 1000, message = "贷款用途长度不能超过 1000")
    private String loanPurpose;

    /** 还款方式 */
    @Size(max = 64, message = "还款方式长度不能超过 64")
    private String repaymentMethod;

    /** 担保方式 */
    @Size(max = 64, message = "担保方式长度不能超过 64")
    private String guaranteeType;
}
