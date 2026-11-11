package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 贷款用途合理性输出：申请信息、用途分类、需求测算、交易背景、资金流向与两类还款来源分开评价。
 * 不包含最终审批金额，AI 只提供测算与合理性判断依据。
 */
@Data
public class LoanPurposeAnalysisData {

    private String creditCode;

    private String enterpriseName;

    private BigDecimal applyAmount;

    private Integer applyTerm;

    private String termUnit;

    private String productName;

    private String loanPurpose;

    /** 用途分类编码 */
    private String purposeCategory;

    /** 用途分类名称 */
    private String purposeCategoryName;

    /** 对应业务场景 */
    private String businessScenario;

    /** 用途合理性：RATIONAL / NEEDS_VERIFICATION / UNKNOWN */
    private String purposeReasonableness;

    private String purposeReasonablenessReason;

    /** 需求测算类型：WORKING_CAPITAL / PROJECT_FUNDING */
    private String demandType;

    /** 测算资金需求 */
    private BigDecimal measuredDemand;

    /** 资金缺口 = 测算需求 − 申请金额，正数表示仍有缺口 */
    private BigDecimal fundingGap;

    /** 测算公式 */
    private String demandFormula;

    /** 公式配置来源 */
    private String demandFormulaSource;

    /** 公式参数 */
    private Map<String, Object> demandParameters;

    /** 金额合理性：RATIONAL / SLIGHTLY_HIGH / EXCESSIVE / INSUFFICIENT / UNKNOWN */
    private String amountReasonableness;

    private String amountReasonablenessReason;

    /** 重点关注金额区间下限 */
    private BigDecimal keyAmountRangeMin;

    /** 重点关注金额区间上限 */
    private BigDecimal keyAmountRangeMax;

    /** 期限合理性：RATIONAL / TOO_SHORT / TOO_LONG / UNKNOWN */
    private String termReasonableness;

    private String termReasonablenessReason;

    private Integer contractCount;

    private Integer orderCount;

    private Integer invoiceCount;

    private BigDecimal totalContractAmount;

    private Boolean contractPurposeConsistent;

    private Boolean invoicesMatched;

    private List<String> counterparties;

    /** 交易背景核验结论 */
    private String transactionBackground;

    /** 资金流向线索，仅作为线索 */
    private List<String> fundFlowClues;

    private Boolean roundTripSuspected;

    private List<String> relatedAccounts;

    private Integer abnormalTransferCount;

    /** 第一还款来源 */
    private String primaryRepaymentSource;

    /** 第一还款来源评价：STRONG / MODERATE / WEAK / UNKNOWN */
    private String primaryRepaymentAssessment;

    /** 第二还款来源 */
    private String secondaryRepaymentSource;

    /** 第二还款来源评价 */
    private String secondaryRepaymentAssessment;

    /** 第二还款来源不能替代第一还款来源 */
    private boolean secondaryCannotReplacePrimary;

    /** 用途异常线索编码 */
    private List<String> anomalies;

    private List<String> investigationSuggestions;

    private List<String> missingItems;

    private Integer evidenceCount;

    /** 审批金额边界说明：AI 不输出最终审批金额 */
    private String approvalAmountNote;
}
