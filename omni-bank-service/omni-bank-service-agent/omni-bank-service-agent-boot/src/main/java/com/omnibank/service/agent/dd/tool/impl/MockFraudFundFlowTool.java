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
 * 资金流向异常核验工具（Mock）：金额相近、时间接近与路径闭合只作为回流线索，不直接认定。
 */
@Component
@RequiredArgsConstructor
public class MockFraudFundFlowTool implements AgentTool<FraudCheckSnapshots.FundFlowResult> {

    public static final String TOOL_CODE = "fraud.checkFundFlowAnomalies";

    private static final String SOURCE_SYSTEM = "MOCK_FUND_FLOW_MONITOR";

    private static final String RULE_VERSION = "1.0";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.62");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "资金流向异常核验（Mock）",
                "识别资金回流、循环转账与异常时段大额转账线索，不作为违规认定", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<FraudCheckSnapshots.FundFlowResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        int abnormalTransferCount = DdDeterministicMockData.intBetween(creditCode,
                "fraud-fund-abnormal-transfer", 0, 3);
        boolean amountClose = DdDeterministicMockData.seed(creditCode,
                "fraud-fund-amount-close") % 2 == 0;
        boolean timeClose = DdDeterministicMockData.seed(creditCode,
                "fraud-fund-time-close") % 2 == 0;
        boolean circularSuspected = amountClose && timeClose && DdDeterministicMockData.seed(creditCode,
                "fraud-fund-circular") % 3 == 0;
        List<String> paths = new ArrayList<>();
        if (circularSuspected) {
            paths.add("借款企业 → 交易对手 A → 疑似关联方 B → 借款企业（金额相近、时间接近）");
        }
        if (abnormalTransferCount > 0) {
            paths.add("借款企业 → 非惯常交易对手（异常时段大额转账 " + abnormalTransferCount + " 笔）");
        }
        List<FraudCheckSnapshots.RuleHit> hits = new ArrayList<>();
        if (circularSuspected) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_FUND_001", RULE_VERSION, "FUND_FLOW", "HIGH",
                    "疑似资金回流",
                    "金额相近=" + amountClose + "，时间接近=" + timeClose + "，路径闭合=是",
                    "存在金额相近、时间接近且路径闭合的资金往返，仅作为回流线索，不能直接认定违规",
                    "调阅完整银行流水与支付凭证，核实交易背景、合同与最终收款方"));
        }
        if (abnormalTransferCount > 0) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_FUND_002", RULE_VERSION, "FUND_FLOW", "MEDIUM",
                    "异常时段大额转账", "异常转账 " + abnormalTransferCount + " 笔",
                    "存在与经营节奏不匹配的大额转账，需核实交易对手与资金用途",
                    "核实交易对手、资金用途与审批记录，必要时开展实地核查"));
        }
        String dataPeriod = YearMonth.now().toString();
        FraudCheckSnapshots.FundFlowResult snapshot = new FraudCheckSnapshots.FundFlowResult(List.copyOf(hits),
                abnormalTransferCount, amountClose, timeClose, circularSuspected, List.copyOf(paths), dataPeriod);
        String summary = "Mock 资金流向异常核验：异常转账 " + abnormalTransferCount + " 笔，金额相近=" + amountClose
                + "，时间接近=" + timeClose + "，回流线索=" + circularSuspected + "，命中规则 " + hits.size() + " 条";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CALCULATION,
                EvidenceSourceType.BANK_INTERFACE, SOURCE_SYSTEM, "FRAUD_FUND_FLOW", subjectId(request),
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
        return MockToolSupport.enterpriseId(request.stringParam("enterpriseId"));
    }
}
