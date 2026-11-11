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
 * 项目融资需求测算工具（Mock）：按项目总投资与自有资金比例测算，与流动资金公式严格区分。
 */
@Component
@RequiredArgsConstructor
public class MockLoanPurposeProjectTool implements AgentTool<LoanPurposeSnapshots.Demand> {

    public static final String TOOL_CODE = "loanPurpose.calculateProjectFundingDemand";

    private static final String SOURCE_SYSTEM = "MOCK_LOAN_PURPOSE_ENGINE";

    private static final String FORMULA = "项目总投资 − 项目自有资金";

    private static final String FORMULA_SOURCE = "MOCK_CONFIG:DD_LOAN_PURPOSE_PROJECT_FUNDING_V1";

    private static final BigDecimal MIN_SELF_FUND_RATIO = new BigDecimal("0.20");

    private static final BigDecimal MAX_SELF_FUND_RATIO = new BigDecimal("0.50");

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.78");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "项目融资需求测算（Mock）",
                "按配置公式测算项目融资需求：项目总投资 − 项目自有资金", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<LoanPurposeSnapshots.Demand> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        BigDecimal totalInvestment = DdDeterministicMockData.baseAmount(creditCode,
                "loan-purpose-project-investment", 3000, 9000);
        BigDecimal selfFundRatio = DdDeterministicMockData.rateBetween(creditCode,
                "loan-purpose-project-self-fund", MIN_SELF_FUND_RATIO, MAX_SELF_FUND_RATIO);
        BigDecimal selfFunds = totalInvestment.multiply(selfFundRatio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal demand = totalInvestment.subtract(selfFunds).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("totalInvestment", totalInvestment);
        parameters.put("selfFundRatio", selfFundRatio);
        parameters.put("selfFunds", selfFunds);
        String dataPeriod = YearMonth.now().toString();
        LoanPurposeSnapshots.Demand snapshot = new LoanPurposeSnapshots.Demand("PROJECT_FUNDING", demand,
                FORMULA, FORMULA_SOURCE, parameters, dataPeriod);
        String summary = "Mock 项目融资需求测算：项目总投资 " + totalInvestment.toPlainString() + " 元，自有资金比例 "
                + selfFundRatio.toPlainString() + "，测算需求 " + demand.toPlainString() + " 元；公式来源 "
                + FORMULA_SOURCE;
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CALCULATION,
                EvidenceSourceType.FINANCIAL_CALCULATION, SOURCE_SYSTEM,
                "LOAN_PURPOSE_DEMAND:PROJECT_FUNDING", subjectId(request), dataPeriod, summary, CONFIDENCE);
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
