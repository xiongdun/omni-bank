package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.support.DdDeterministicMockData;
import com.omnibank.service.agent.dd.support.DdMockEvidence;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 财务异常核验工具（Mock）：比对报表、纳税申报与银行流水，差异同时列出可能合理原因。
 */
@Component
@RequiredArgsConstructor
public class MockFraudFinancialTool implements AgentTool<FraudCheckSnapshots.FinancialResult> {

    public static final String TOOL_CODE = "fraud.checkFinancialAnomalies";

    private static final String SOURCE_SYSTEM = "MOCK_FINANCIAL_DATA_HUB";

    private static final String RULE_VERSION = "1.0";

    private static final BigDecimal DEVIATION_THRESHOLD = new BigDecimal("0.10");

    private static final BigDecimal INFLOW_COVERAGE_THRESHOLD = new BigDecimal("0.60");

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.74");

    private static final List<String> REASONABLE_CAUSES = List.of(
            "统计期间不一致（纳税申报期间与报表期间错位）",
            "未开票收入或预收款项导致纳税申报收入偏低",
            "现金结算、个人账户收款等非本行账户流水未纳入统计",
            "关联交易定价或内部交易抵消口径差异",
            "收入在集团内部重复统计或合并抵消",
            "发票开具时间与收入确认时间存在跨期");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "财务异常核验（Mock）",
                "比对报表收入、纳税申报收入与银行流水，输出差异线索并列出可能合理原因", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<FraudCheckSnapshots.FinancialResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        BigDecimal statementRevenue = DdDeterministicMockData.baseAmount(creditCode,
                "fraud-statement-revenue", 800, 3000);
        BigDecimal declaredRevenue = statementRevenue.multiply(DdDeterministicMockData.rateBetween(creditCode,
                "fraud-declared-revenue-rate", new BigDecimal("0.72"), new BigDecimal("0.88")))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal bankInflow = statementRevenue.multiply(DdDeterministicMockData.rateBetween(creditCode,
                "fraud-bank-inflow-rate", new BigDecimal("0.35"), new BigDecimal("0.75")))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal deviation = DdDeterministicMockData.divide(
                declaredRevenue.subtract(statementRevenue), statementRevenue, 4);
        BigDecimal coverage = DdDeterministicMockData.divide(bankInflow, statementRevenue, 4);
        List<FraudCheckSnapshots.RuleHit> hits = new ArrayList<>();
        if (deviation != null && deviation.abs().compareTo(DEVIATION_THRESHOLD) > 0) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_FIN_001", RULE_VERSION, "FINANCIAL", "HIGH",
                    "纳税申报收入与报表收入差异较大",
                    "报表收入 " + statementRevenue.toPlainString() + " 元，纳税申报收入 "
                            + declaredRevenue.toPlainString() + " 元，偏离 " + percent(deviation),
                    "纳税申报收入与报表收入偏离超过 10%，需核实收入确认口径与差异原因",
                    "取得纳税申报表、收入明细账与开票记录，逐项核对差异原因"));
        }
        if (coverage != null && coverage.compareTo(INFLOW_COVERAGE_THRESHOLD) < 0) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_FIN_002", RULE_VERSION, "FINANCIAL", "MEDIUM",
                    "银行流水经营流入覆盖不足",
                    "经营流入 " + bankInflow.toPlainString() + " 元，覆盖报表收入 " + percent(coverage),
                    "银行流水经营流入低于报表收入 60%，可能存在收入虚增或流水未归集",
                    "核实主要结算账户、现金交易与关联方回款路径"));
        }
        String dataPeriod = YearMonth.now().toString();
        FraudCheckSnapshots.FinancialResult snapshot = new FraudCheckSnapshots.FinancialResult(List.copyOf(hits),
                statementRevenue, declaredRevenue, bankInflow, deviation, REASONABLE_CAUSES, dataPeriod);
        String summary = "Mock 财务异常核验：报表收入 " + statementRevenue.toPlainString() + " 元，申报收入偏离 "
                + percent(deviation) + "，流水覆盖 " + percent(coverage) + "，命中规则 " + hits.size() + " 条";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.FINANCIAL_STATEMENT,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM, "FRAUD_FINANCIAL", subjectId(request),
                dataPeriod, summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true));
    }

    private String percent(BigDecimal value) {
        return value == null ? "-" : value.multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        return MockToolSupport.enterpriseId(request.stringParam("enterpriseId"));
    }
}
