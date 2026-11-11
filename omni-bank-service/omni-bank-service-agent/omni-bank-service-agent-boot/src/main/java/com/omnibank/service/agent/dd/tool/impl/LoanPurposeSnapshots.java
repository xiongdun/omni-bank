package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 贷款用途工具快照：工具只负责取数与计算，合理性判断在 Agent 内完成
 */
public final class LoanPurposeSnapshots {

    private LoanPurposeSnapshots() {
    }

    /** 用途分类结果 */
    public record Classification(String purposeCategory, String categoryName, String businessScenario,
                                 String matchedKeyword, String dataPeriod) {
    }

    /** 资金需求测算结果，formulaSource 记录配置来源 */
    public record Demand(String demandType, BigDecimal measuredDemand, String formula, String formulaSource,
                         Map<String, Object> parameters, String dataPeriod) {
    }

    /** 合同、订单与发票核验结果 */
    public record Contract(int contractCount, int orderCount, int invoiceCount, BigDecimal totalContractAmount,
                           List<String> counterparties, boolean purposeConsistent, boolean invoicesMatched,
                           List<String> issues, String dataPeriod) {
    }

    /** 资金流向线索：仅作为线索，不直接认定违规 */
    public record FundFlow(List<String> relatedAccounts, int abnormalTransferCount, boolean roundTripSuspected,
                           List<String> clues, String dataPeriod) {
    }
}
