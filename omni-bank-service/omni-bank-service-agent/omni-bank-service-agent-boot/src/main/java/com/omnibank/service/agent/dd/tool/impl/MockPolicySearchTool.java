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
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 制度检索工具（Mock RAG）：按产品、用途与行业匹配本地 Mock 制度库，只返回制度元数据，
 * 相关命中不代表条款适用，更不代表合规结论。
 */
@Component
@RequiredArgsConstructor
public class MockPolicySearchTool implements AgentTool<PolicySnapshots.SearchResult> {

    public static final String TOOL_CODE = "policy.searchApplicablePolicies";

    private static final String SOURCE_SYSTEM = "MOCK_POLICY_REGISTRY";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.85");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "制度检索（Mock RAG）",
                "按产品编码、贷款用途与行业检索适用制度元数据，返回制度名称、版本与生效日期", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<PolicySnapshots.SearchResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String productCode = request.stringParam("productCode");
        String loanPurpose = request.stringParam("loanPurpose");
        String industryName = request.stringParam("industryName");
        List<PolicySnapshots.PolicySummary> matched = MockPolicyCatalog.all().stream()
                .filter(policy -> matchProduct(policy, productCode))
                .filter(policy -> matchPurpose(policy, loanPurpose))
                .map(MockPolicySearchTool::summary)
                .toList();
        String dataPeriod = YearMonth.now().toString();
        String query = "产品=" + dash(productCode) + "；用途=" + dash(loanPurpose) + "；行业=" + dash(industryName);
        PolicySnapshots.SearchResult snapshot = new PolicySnapshots.SearchResult(matched, query, dataPeriod);
        String summary = matched.isEmpty()
                ? "Mock 制度检索未命中：" + query
                : "Mock 制度检索命中 " + matched.size() + " 部制度：" + String.join("、",
                        matched.stream().map(PolicySnapshots.PolicySummary::policyName).toList()) + "；" + query;
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.POLICY_DOCUMENT,
                EvidenceSourceType.POLICY_DOCUMENT, SOURCE_SYSTEM, "POLICY_SEARCH", subjectId(request),
                dataPeriod, summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "matchedCount", matched.size()));
    }

    private static PolicySnapshots.PolicySummary summary(MockPolicyCatalog.PolicyDoc policy) {
        return new PolicySnapshots.PolicySummary(policy.policyId(), policy.policyName(), policy.policyVersion(),
                policy.effectiveDate(), policy.expiryDate(), policy.status(), policy.sourceFile(), policy.fileHash());
    }

    private boolean matchProduct(MockPolicyCatalog.PolicyDoc policy, String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return true;
        }
        String normalized = productCode.trim().toUpperCase(Locale.ROOT);
        return policy.applicableProducts().stream()
                .anyMatch(product -> "*".equals(product) || normalized.contains(product)
                        || product.contains(normalized));
    }

    private boolean matchPurpose(MockPolicyCatalog.PolicyDoc policy, String loanPurpose) {
        if (loanPurpose == null || loanPurpose.isBlank()) {
            return true;
        }
        return policy.applicablePurposes().stream()
                .anyMatch(purpose -> "*".equals(purpose) || loanPurpose.contains(purpose));
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        return MockToolSupport.enterpriseId(request.stringParam("enterpriseId"));
    }

    private String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
