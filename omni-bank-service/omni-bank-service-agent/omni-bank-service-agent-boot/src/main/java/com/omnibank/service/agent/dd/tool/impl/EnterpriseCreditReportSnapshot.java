package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 企业征信报告快照：授信使用、逾期、对外担保与近 12 个月还款记录
 */
public record EnterpriseCreditReportSnapshot(String creditCode, BigDecimal creditLimit, BigDecimal usedCredit,
                                             BigDecimal availableCredit, int overdueCount,
                                             BigDecimal overdueAmount, BigDecimal externalGuaranteeBalance,
                                             BigDecimal netAssets, List<RepaymentRecord> repaymentRecords,
                                             List<ToolEvidenceRef> evidences) {

    /**
     * 单月还款记录，status 取值 NORMAL / OVERDUE
     */
    public record RepaymentRecord(String month, BigDecimal repaymentAmount, String status) {
    }
}
