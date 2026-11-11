package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 反欺诈工具快照：每条命中都必须带规则编号与规则版本，便于回溯规则口径
 */
public final class FraudCheckSnapshots {

    private FraudCheckSnapshots() {
    }

    /** 单条规则命中，只作为待核实线索 */
    public record RuleHit(String ruleCode, String ruleVersion, String category, String severity, String title,
                          String hitData, String description, String suggestion) {
    }

    /** 主体一致性核验结果 */
    public record IdentityResult(List<RuleHit> hits, String matchedName, String matchMode, String dataPeriod) {
    }

    /** 关联关系核验结果 */
    public record RelatedPartyResult(List<RuleHit> hits, List<String> relationPaths, String dataPeriod) {
    }

    /** 财务异常核验结果，reasonableCauses 用于区分差异的合理原因 */
    public record FinancialResult(List<RuleHit> hits, BigDecimal statementRevenue, BigDecimal declaredRevenue,
                                  BigDecimal bankInflow, BigDecimal revenueDeviation,
                                  List<String> reasonableCauses, String dataPeriod) {
    }

    /** 合同与材料异常核验结果 */
    public record ContractResult(List<RuleHit> hits, int contractCount, int duplicateCount,
                                 List<String> counterparties, String dataPeriod) {
    }

    /** 资金流向异常核验结果，金额相近/时间接近只能作为线索 */
    public record FundFlowResult(List<RuleHit> hits, int abnormalTransferCount, boolean amountClose,
                                 boolean timeClose, boolean circularSuspected, List<String> paths,
                                 String dataPeriod) {
    }
}
