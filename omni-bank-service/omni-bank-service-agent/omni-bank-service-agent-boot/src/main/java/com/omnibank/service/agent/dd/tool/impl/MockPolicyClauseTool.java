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
 * 制度条款检索工具（Mock RAG）：按制度 ID 返回条款原文、页码、版本、来源文件与文件哈希，
 * 每条条款独立登记证据，保证输出可回溯到原文。
 */
@Component
@RequiredArgsConstructor
public class MockPolicyClauseTool implements AgentTool<PolicySnapshots.ClauseResult> {

    public static final String TOOL_CODE = "policy.retrievePolicyClauses";

    private static final String SOURCE_SYSTEM = "MOCK_POLICY_REGISTRY";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.88");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "制度条款检索（Mock RAG）",
                "按制度 ID 返回条款原文、条款号、页码、版本、生效日期、来源文件与文件哈希", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<PolicySnapshots.ClauseResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String policyId = request.stringParam("policyId");
        if (policyId == null || policyId.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "policyId 不能为空");
        }
        return MockPolicyCatalog.find(policyId)
                .map(policy -> success(request, policy))
                .orElseGet(() -> ToolResult.notFound(SOURCE_SYSTEM, "制度不存在：" + policyId));
    }

    private ToolResult<PolicySnapshots.ClauseResult> success(ToolRequest request,
                                                             MockPolicyCatalog.PolicyDoc policy) {
        String dataPeriod = YearMonth.now().toString();
        List<PolicySnapshots.Clause> clauses = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        for (MockPolicyCatalog.ClauseTemplate template : policy.clauses()) {
            String vectorId = "POLICY-" + policy.policyId() + "-" + template.clauseNo();
            PolicySnapshots.Clause clause = new PolicySnapshots.Clause(policy.policyId(), policy.policyName(),
                    policy.policyVersion(), policy.effectiveDate(), policy.expiryDate(), policy.status(),
                    template.sectionNo(), template.clauseNo(), template.pageNo(), template.originalText(),
                    policy.sourceFile(), policy.fileHash(), vectorId, template.approvalAuthorityNote());
            clauses.add(clause);
            String summary = "Mock 制度条款：" + policy.policyName() + " " + policy.policyVersion() + " "
                    + template.clauseNo() + "（" + template.pageNo() + "）：" + template.originalText();
            ExternalDataItem item = DdMockEvidence.item(EvidenceType.POLICY_DOCUMENT,
                    EvidenceSourceType.POLICY_DOCUMENT, SOURCE_SYSTEM,
                    "POLICY_CLAUSE:" + policy.policyId() + ":" + template.clauseNo(), subjectId(request),
                    dataPeriod, summary, CONFIDENCE);
            evidenceIds.add(String.valueOf(evidenceService.recordEvidence(request.taskId(),
                    enterpriseId(request), item)));
        }
        PolicySnapshots.ClauseResult snapshot = new PolicySnapshots.ClauseResult(policy.policyId(),
                List.copyOf(clauses), dataPeriod);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.copyOf(evidenceIds),
                Map.of("mock", true, "clauseCount", clauses.size()));
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        return MockToolSupport.enterpriseId(request.stringParam("enterpriseId"));
    }
}
