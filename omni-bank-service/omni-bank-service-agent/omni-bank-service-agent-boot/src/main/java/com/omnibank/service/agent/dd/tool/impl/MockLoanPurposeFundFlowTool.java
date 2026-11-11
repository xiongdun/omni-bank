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
 * 资金流向分析工具（Mock）：输出关联账户、异常转账与回流线索，只作为线索不做违规认定。
 */
@Component
@RequiredArgsConstructor
public class MockLoanPurposeFundFlowTool implements AgentTool<LoanPurposeSnapshots.FundFlow> {

    public static final String TOOL_CODE = "loanPurpose.analyzeFundFlow";

    private static final String SOURCE_SYSTEM = "MOCK_FUND_FLOW_MONITOR";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.65");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "资金流向分析（Mock）",
                "识别资金用途、交易对手、关联账户、异常转账与资金回流线索，仅作为风险线索", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<LoanPurposeSnapshots.FundFlow> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        int relatedAccountCount = DdDeterministicMockData.intBetween(creditCode,
                "loan-purpose-related-accounts", 0, 3);
        int abnormalTransferCount = DdDeterministicMockData.intBetween(creditCode,
                "loan-purpose-abnormal-transfer", 0, 3);
        boolean roundTripSuspected = DdDeterministicMockData.seed(creditCode, "loan-purpose-round-trip") % 3 == 0;
        List<String> relatedAccounts = new ArrayList<>();
        for (int i = 1; i <= relatedAccountCount; i++) {
            relatedAccounts.add("疑似关联账户-" + i);
        }
        List<String> clues = new ArrayList<>();
        if (roundTripSuspected) {
            clues.add("存在金额相近、时间接近的资金往返路径，仅作为回流线索，需核实交易路径与业务背景");
        }
        if (abnormalTransferCount > 0) {
            clues.add("检测到 " + abnormalTransferCount + " 笔异常时段大额转账，需核实交易对手与用途");
        }
        if (!relatedAccounts.isEmpty()) {
            clues.add("识别到 " + relatedAccountCount + " 个疑似关联账户，需核实关联关系");
        }
        if (clues.isEmpty()) {
            clues.add("未发现明显资金流向异常线索");
        }
        String dataPeriod = YearMonth.now().toString();
        LoanPurposeSnapshots.FundFlow snapshot = new LoanPurposeSnapshots.FundFlow(List.copyOf(relatedAccounts),
                abnormalTransferCount, roundTripSuspected, List.copyOf(clues), dataPeriod);
        String summary = "Mock 资金流向分析：疑似关联账户 " + relatedAccountCount + " 个，异常转账 "
                + abnormalTransferCount + " 笔，回流线索=" + roundTripSuspected + "；" + String.join("；", clues);
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CALCULATION,
                EvidenceSourceType.BANK_INTERFACE, SOURCE_SYSTEM, "LOAN_PURPOSE_FUND_FLOW", subjectId(request),
                dataPeriod, summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true));
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
