package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
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
 * 主体一致性核验工具（Mock）：名称模糊匹配只输出待核实线索，不直接形成欺诈结论。
 */
@Component
@RequiredArgsConstructor
public class MockFraudIdentityTool implements AgentTool<FraudCheckSnapshots.IdentityResult> {

    public static final String TOOL_CODE = "fraud.checkIdentityConsistency";

    private static final String SOURCE_SYSTEM = "MOCK_ENTERPRISE_REGISTRY";

    private static final String RULE_VERSION = "1.0";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.70");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "主体一致性核验（Mock）",
                "核验申报主体名称、统一社会信用代码与工商登记信息的一致性", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<FraudCheckSnapshots.IdentityResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        String enterpriseName = request.stringParam("enterpriseName");
        boolean fuzzy = MockToolSupport.fuzzyMatch(creditCode);
        List<FraudCheckSnapshots.RuleHit> hits = new ArrayList<>();
        if (fuzzy) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_ID_001", RULE_VERSION, "IDENTITY", "MEDIUM",
                    "主体名称近似匹配",
                    "申报名称：" + (enterpriseName == null ? "-" : enterpriseName) + "，匹配模式：FUZZY",
                    "工商登记名称与申报名称不完全一致，可能为名称变更、录入偏差或近似主体",
                    "人工核对营业执照原件、工商登记信息与名称变更记录"));
        }
        String dataPeriod = YearMonth.now().toString();
        FraudCheckSnapshots.IdentityResult snapshot = new FraudCheckSnapshots.IdentityResult(List.copyOf(hits),
                enterpriseName == null ? "-" : enterpriseName, fuzzy ? "FUZZY" : "EXACT", dataPeriod);
        String summary = "Mock 主体一致性核验：匹配模式 " + snapshot.matchMode() + "，命中规则 " + hits.size() + " 条";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.ENTERPRISE_PROFILE,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM, "FRAUD_IDENTITY", subjectId(request),
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
