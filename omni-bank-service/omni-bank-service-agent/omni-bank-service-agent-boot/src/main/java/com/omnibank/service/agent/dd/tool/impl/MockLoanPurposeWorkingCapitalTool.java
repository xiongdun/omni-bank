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
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 流动资金需求测算工具（Mock）：公式与参数来自 Mock 配置，不与项目融资公式混用。
 */
@Component
@RequiredArgsConstructor
public class MockLoanPurposeWorkingCapitalTool implements AgentTool<LoanPurposeSnapshots.Demand> {

    public static final String TOOL_CODE = "loanPurpose.calculateWorkingCapitalDemand";

    private static final String SOURCE_SYSTEM = "MOCK_LOAN_PURPOSE_ENGINE";

    private static final String FORMULA = "年营业收入 ÷ 360 × 营运资金周转天数 − 自有资金";

    private static final String FORMULA_SOURCE = "MOCK_CONFIG:DD_LOAN_PURPOSE_WORKING_CAPITAL_V1";

    private static final BigDecimal SELF_FUND_RATIO = new BigDecimal("0.30");

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.80");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "流动资金需求测算（Mock）",
                "按配置公式测算流动资金需求：年营业收入 ÷ 360 × 周转天数 − 自有资金", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<LoanPurposeSnapshots.Demand> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        BigDecimal annualRevenue = DdDeterministicMockData.baseAmount(creditCode, "loan-purpose-revenue", 800, 3000);
        int turnoverDays = DdDeterministicMockData.intBetween(creditCode, "loan-purpose-turnover-days", 60, 120);
        BigDecimal requirement = annualRevenue.multiply(BigDecimal.valueOf(turnoverDays))
                .divide(BigDecimal.valueOf(360), 2, RoundingMode.HALF_UP);
        BigDecimal selfFunds = requirement.multiply(SELF_FUND_RATIO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal demand = requirement.subtract(selfFunds).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("annualRevenue", annualRevenue);
        parameters.put("turnoverDays", turnoverDays);
        parameters.put("selfFundRatio", SELF_FUND_RATIO);
        parameters.put("selfFunds", selfFunds);
        String dataPeriod = YearMonth.now().toString();
        LoanPurposeSnapshots.Demand snapshot = new LoanPurposeSnapshots.Demand("WORKING_CAPITAL", demand,
                FORMULA, FORMULA_SOURCE, parameters, dataPeriod);
        String summary = "Mock 流动资金需求测算：年营业收入 " + annualRevenue.toPlainString() + " 元，周转天数 "
                + turnoverDays + " 天，测算需求 " + demand.toPlainString() + " 元；公式来源 " + FORMULA_SOURCE;
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CALCULATION,
                EvidenceSourceType.FINANCIAL_CALCULATION, SOURCE_SYSTEM,
                "LOAN_PURPOSE_DEMAND:WORKING_CAPITAL", subjectId(request), dataPeriod, summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "formulaSource", FORMULA_SOURCE));
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        String value = request.stringParam("enterpriseId");
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }
}
