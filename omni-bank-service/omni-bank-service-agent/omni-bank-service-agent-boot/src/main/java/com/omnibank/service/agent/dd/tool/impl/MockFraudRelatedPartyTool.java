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
 * 关联关系核验工具（Mock）：识别疑似隐性关联方与共同地址/电话，只输出待核实线索。
 */
@Component
@RequiredArgsConstructor
public class MockFraudRelatedPartyTool implements AgentTool<FraudCheckSnapshots.RelatedPartyResult> {

    public static final String TOOL_CODE = "fraud.checkRelatedPartyTransactions";

    private static final String SOURCE_SYSTEM = "MOCK_RELATED_PARTY_GRAPH";

    private static final String RULE_VERSION = "1.0";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.66");

    private static final List<String> RELATED_PARTY_POOL = List.of(
            "江西恒达贸易有限公司", "深圳远洋投资有限公司", "杭州联创供应链有限公司");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "关联关系核验（Mock）",
                "识别股权、人员、地址、电话等维度的疑似关联关系并输出关系路径", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<FraudCheckSnapshots.RelatedPartyResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        int relatedCount = DdDeterministicMockData.intBetween(creditCode, "fraud-related-party", 0, 3);
        boolean sharedContact = DdDeterministicMockData.seed(creditCode, "fraud-related-shared-contact") % 4 == 0;
        List<FraudCheckSnapshots.RuleHit> hits = new ArrayList<>();
        List<String> relationPaths = new ArrayList<>();
        for (int i = 0; i < relatedCount; i++) {
            String party = RELATED_PARTY_POOL.get((int) ((DdDeterministicMockData.seed(creditCode,
                    "fraud-related-name") + i) % RELATED_PARTY_POOL.size()));
            relationPaths.add("借款企业 → 共同联系人/股东 → " + party);
        }
        if (relatedCount > 0) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_REL_001", RULE_VERSION, "RELATED_PARTY", "MEDIUM",
                    "疑似隐性关联关系", "疑似关联方 " + relatedCount + " 个，关系路径 " + relationPaths.size() + " 条",
                    "股权、人员或联系方式存在交叉，可能构成隐性关联关系", "核实股权结构、实际控制人与关联交易定价"));
        }
        if (sharedContact) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_REL_002", RULE_VERSION, "RELATED_PARTY", "MEDIUM",
                    "注册地址或联系电话重合", "与疑似关联方存在相同联系电话或注册地址",
                    "地址/电话重合可能为集团统一管理，也可能是空壳或包装主体", "实地核查经营场所与人员办公情况"));
            relationPaths.add("借款企业 → 相同联系电话/地址 → 疑似关联方");
        }
        String dataPeriod = YearMonth.now().toString();
        FraudCheckSnapshots.RelatedPartyResult snapshot = new FraudCheckSnapshots.RelatedPartyResult(
                List.copyOf(hits), List.copyOf(relationPaths), dataPeriod);
        String summary = "Mock 关联关系核验：疑似关联方 " + relatedCount + " 个，关系路径 " + relationPaths.size()
                + " 条，命中规则 " + hits.size() + " 条";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.ENTERPRISE_PROFILE,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM, "FRAUD_RELATED_PARTY", subjectId(request),
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
