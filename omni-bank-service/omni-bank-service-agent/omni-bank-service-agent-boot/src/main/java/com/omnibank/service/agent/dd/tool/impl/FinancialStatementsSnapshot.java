package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务报表快照：按年度返回三大报表关键科目，工具层不做业务判断
 */
public record FinancialStatementsSnapshot(String creditCode, List<StatementPeriod> periods,
                                          List<ToolEvidenceRef> evidences) {

    /**
     * 单期财务报表关键科目，金额统一为元、保留 2 位小数
     */
    public record StatementPeriod(String period, BigDecimal totalAssets, BigDecimal currentAssets,
                                  BigDecimal cash, BigDecimal accountsReceivable, BigDecimal inventory,
                                  BigDecimal totalLiabilities, BigDecimal currentLiabilities,
                                  BigDecimal shortTermDebt, BigDecimal netAssets, BigDecimal revenue,
                                  BigDecimal costOfSales, BigDecimal netProfit, BigDecimal interestExpense,
                                  BigDecimal ebitda, BigDecimal operatingCashFlow, BigDecimal investingCashFlow,
                                  BigDecimal financingCashFlow) {
    }
}
