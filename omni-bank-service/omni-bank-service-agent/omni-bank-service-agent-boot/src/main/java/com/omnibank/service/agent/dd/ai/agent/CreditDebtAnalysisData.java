package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 征信债务分析输出：授信使用、逾期、担保、债务期限结构与债务负担
 */
@Data
public class CreditDebtAnalysisData {

    private String creditCode;

    private BigDecimal creditLimit;

    private BigDecimal usedCredit;

    private BigDecimal availableCredit;

    /** 征信使用率：已用授信/授信总额 */
    private BigDecimal creditUtilization;

    private Integer overdueCount;

    private BigDecimal overdueAmount;

    /** 逾期率：逾期金额/已用授信 */
    private BigDecimal overdueRate;

    private BigDecimal externalGuaranteeBalance;

    /** 对外担保/净资产 */
    private BigDecimal guaranteeToNetAssetsRatio;

    private Integer repaymentRecordCount;

    /** 近 12 个月还款记录中逾期期数 */
    private Integer overdueRepaymentMonths;

    private BigDecimal shortTermDebt;

    private BigDecimal midTermDebt;

    private BigDecimal longTermDebt;

    private BigDecimal totalInterestBearingDebt;

    private BigDecimal dueWithin6Months;

    private BigDecimal dueWithin12Months;

    /** 短债占比：短期有息负债/有息负债合计 */
    private BigDecimal shortTermDebtRatio;

    /** 未来 6 个月到期占比 */
    private BigDecimal dueWithin6MonthsRatio;

    /** 债务负担：（有息负债 + 本次申请金额）/ 营业收入 */
    private BigDecimal debtToRevenueRatio;

    /** 实际采用的营业收入口径 */
    private BigDecimal revenueUsed;

    /** 可引用证据数量 */
    private Integer evidenceCount;
}
