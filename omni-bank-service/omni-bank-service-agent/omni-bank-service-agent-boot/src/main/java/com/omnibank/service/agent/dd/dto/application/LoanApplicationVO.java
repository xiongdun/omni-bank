package com.omnibank.service.agent.dd.dto.application;

import com.omnibank.common.core.vo.BaseVO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 授信申请视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoanApplicationVO extends BaseVO {

    private Long id;

    /** 原信贷系统申请编号 */
    private String applicationNo;

    /** 原始申请业务类型 */
    private String applicationType;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 申请金额 */
    private BigDecimal applyAmount;

    /** 币种 */
    private String currency;

    /** 申请期限 */
    private Integer applyTerm;

    /** 期限单位 */
    private String termUnit;

    /** 贷款用途 */
    private String loanPurpose;

    /** 还款方式 */
    private String repaymentMethod;

    /** 担保方式 */
    private String guaranteeType;
}
