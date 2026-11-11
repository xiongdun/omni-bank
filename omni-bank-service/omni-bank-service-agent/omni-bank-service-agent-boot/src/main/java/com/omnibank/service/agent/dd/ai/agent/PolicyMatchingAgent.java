package com.omnibank.service.agent.dd.ai.agent;

import com.omnibank.service.agent.dd.ai.AgentContext;
import com.omnibank.service.agent.dd.ai.AgentDataGap;
import com.omnibank.service.agent.dd.ai.AgentDefinition;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentFinding;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentSupplementRequest;
import com.omnibank.service.agent.dd.ai.CreditAgent;
import com.omnibank.service.agent.dd.ai.EvidenceRef;
import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.impl.PolicySnapshots;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 10 审批政策匹配：第一期只做制度条款检索与清单化输出，不做合规结论。
 * 制度名称、版本、条款原文、页码、限制条款与审批权限关注点全部来自 Mock 制度库，输出可回溯原文。
 */
@Component
@RequiredArgsConstructor
public class PolicyMatchingAgent implements CreditAgent<Void, PolicyMatchingData> {

    public static final String AGENT_CODE = "POLICY_MATCHING";

    private static final String TOOL_SEARCH = "policy.searchApplicablePolicies";

    private static final String TOOL_CLAUSES = "policy.retrievePolicyClauses";

    private static final String POLICY_WORKING_CAPITAL = "POL-WC-2025";

    private static final String POLICY_FIXED_ASSET = "POL-FA-2024";

    private static final String POLICY_ADMISSION = "POL-ADM-2026";

    private static final String STATUS_APPLICABLE = "APPLICABLE";

    private static final String STATUS_NEEDS_MANUAL = "NEEDS_MANUAL_CONFIRMATION";

    private static final String STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";

    private static final String APPLICABILITY_NOTE =
            "制度检索相关性不等于政策适用性，政策适用性也不等于合规结论；全部条款均需人工核对现行制度原文";

    private static final String COMPLIANCE_NOTE = "AI 不输出合规结论，审批结论由有权审批人依据现行制度作出";

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "审批政策匹配 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<PolicyMatchingData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        Set<String> pending = new LinkedHashSet<>();
        Set<String> conflictSet = new LinkedHashSet<>();
        Set<String> limitationSet = new LinkedHashSet<>();
        Set<String> approvalNotes = new LinkedHashSet<>();
        PolicyMatchingData data = new PolicyMatchingData();
        data.setMatchedPolicies(new ArrayList<>());
        data.setClauseMatches(new ArrayList<>());
        data.setChecklist(new ArrayList<>());
        data.setPolicyConflicts(new ArrayList<>());
        data.setPendingManualConfirmations(new ArrayList<>());
        data.setLimitations(new ArrayList<>());
        data.setApplicabilityNote(APPLICABILITY_NOTE);
        data.setComplianceConclusionNote(COMPLIANCE_NOTE);

        if (context.isMockMode()) {
            warnings.add("当前证据来自 Mock Adapter（isMock=true），结论仅用于链路验证");
        }
        warnings.add("当前制度检索基于 Mock 制度库（MOCK_POLICY_REGISTRY），非真实银行制度文件，条款适用性需人工确认");

        fillApplication(data, context);
        if (data.getIndustryName() == null || data.getIndustryName().isBlank()) {
            missingItems.add("未提供行业信息，行业准入匹配受限");
            pending.add("未提供行业信息，行业准入需人工确认");
        }

        Map<String, Object> searchParams = searchParams(data, context);
        ToolRequest searchRequest = new ToolRequest(context.getTaskId(), searchParams);
        ToolResult<PolicySnapshots.SearchResult> searchResult = call(TOOL_SEARCH, searchRequest);
        warnIfMock(TOOL_SEARCH, searchResult, warnings);
        if (!searchResult.isSuccess() || searchResult.getData() == null) {
            gaps.add(dataGap("POLICY_SEARCH", "适用制度检索", "制度检索工具未返回结果", true, "UNAVAILABLE",
                    "无法匹配审批政策", "重新执行制度检索或人工确认适用制度"));
            supplementRequests.add(supplement("POLICY_SEARCH", "适用制度检索",
                    "制度检索失败", "由政策管理员确认适用制度目录"));
            pending.add("制度检索失败，需人工确认适用制度与条款");
            data.setMissingItems(missingItems);
            data.setPendingManualConfirmations(List.copyOf(pending));
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.40"), BigDecimal.ZERO, "政策匹配未完成：制度检索失败");
        }
        addSearchEvidence(evidenceRefs, searchResult, data);

        List<PolicySnapshots.PolicySummary> matched = searchResult.getData().policies();
        if (matched.isEmpty()) {
            gaps.add(dataGap("POLICY_SEARCH", "适用制度检索", "未检索到与产品/用途匹配的制度", true, "NOT_PROVIDED",
                    "无法输出条款级匹配与检查清单", "人工选择适用制度后重新执行政策匹配"));
            supplementRequests.add(supplement("POLICY_SEARCH", "适用制度检索",
                    "未检索到匹配制度", "明确产品与贷款用途，或由政策管理员指定适用制度"));
            pending.add("未检索到适用制度条款，需人工确认产品与用途对应的制度");
            data.setMissingItems(missingItems);
            data.setPendingManualConfirmations(List.copyOf(pending));
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.50"), BigDecimal.ZERO, "政策匹配完成：未检索到适用制度，需人工确认");
        }

        for (PolicySnapshots.PolicySummary summary : matched) {
            data.getMatchedPolicies().add(toSummaryItem(summary));
            loadClauses(context, summary, data, evidenceRefs, pending, conflictSet, limitationSet, approvalNotes,
                    findings, gaps, warnings);
        }

        data.setApprovalAuthorityFocus(approvalNotes.isEmpty()
                ? "Mock 制度库未检索到明确审批权限条款，需人工核对现行授权文件"
                : String.join("；", approvalNotes));
        buildChecklist(data, matched, limitationSet);
        if (isCrossScenario(data.getLoanPurpose()) && matched.size() > 1) {
            conflictSet.add("申请用途同时涉及流动资金与固定资产场景，可能跨制度适用，需人工确认主适用制度");
        }
        data.getPolicyConflicts().addAll(conflictSet);
        data.getLimitations().addAll(limitationSet);
        pending.add("所有数值阈值、审批权限与例外情形须以现行制度原文为准，AI 不输出合规结论");
        data.getPendingManualConfirmations().addAll(pending);
        data.setEvidenceCount(evidenceRefs.size());
        data.setMissingItems(missingItems);

        boolean hasClause = !data.getClauseMatches().isEmpty();
        BigDecimal confidence = hasClause ? new BigDecimal("0.85") : new BigDecimal("0.55");
        BigDecimal completeness = hasClause ? BigDecimal.ONE : BigDecimal.ZERO;
        String summary = "政策匹配完成：命中制度 " + data.getMatchedPolicies().size() + " 部，条款 "
                + data.getClauseMatches().size() + " 条，待人工确认 " + pending.size() + " 项，制度冲突 "
                + conflictSet.size() + " 项；" + APPLICABILITY_NOTE;
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    private void fillApplication(PolicyMatchingData data, AgentContext context) {
        LoanApplicationVO application = context.getCreditApplication();
        if (application != null) {
            data.setProductCode(application.getProductCode());
            data.setProductName(application.getProductName());
            data.setLoanPurpose(application.getLoanPurpose());
            data.setApplyAmount(application.getApplyAmount());
            data.setApplyTerm(application.getApplyTerm());
            data.setTermUnit(application.getTermUnit());
        }
        if (context.getEnterpriseProfile() != null) {
            data.setIndustryName(context.getEnterpriseProfile().getIndustryName());
        }
    }

    private void loadClauses(AgentContext context, PolicySnapshots.PolicySummary summary, PolicyMatchingData data,
                             List<EvidenceRef> evidenceRefs, Set<String> pending, Set<String> conflicts,
                             Set<String> limitations, Set<String> approvalNotes, List<AgentFinding> findings,
                             List<AgentDataGap> gaps, List<String> warnings) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("policyId", summary.policyId());
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        ToolResult<PolicySnapshots.ClauseResult> clauseResult = call(TOOL_CLAUSES,
                new ToolRequest(context.getTaskId(), params));
        warnIfMock(TOOL_CLAUSES, clauseResult, warnings);
        if (!clauseResult.isSuccess() || clauseResult.getData() == null) {
            gaps.add(dataGap("POLICY_CLAUSE", "制度条款",
                    "制度【" + summary.policyName() + "】条款检索失败", true, "UNAVAILABLE",
                    "无法输出可回溯条款", "重新检索该制度条款"));
            return;
        }
        List<PolicySnapshots.Clause> clauses = clauseResult.getData().clauses();
        List<String> evidenceIds = clauseResult.getEvidenceIds();
        for (int i = 0; i < clauses.size(); i++) {
            PolicySnapshots.Clause clause = clauses.get(i);
            PolicyMatchingData.PolicyClauseMatch match = new PolicyMatchingData.PolicyClauseMatch();
            match.setPolicyId(clause.policyId());
            match.setPolicyName(clause.policyName());
            match.setPolicyVersion(clause.policyVersion());
            match.setEffectiveDate(clause.effectiveDate());
            match.setSectionNo(clause.sectionNo());
            match.setClauseNo(clause.clauseNo());
            match.setPageNo(clause.pageNo());
            match.setOriginalText(clause.originalText());
            match.setSourceFile(clause.sourceFile());
            match.setFileHash(clause.fileHash());
            match.setVectorId(clause.vectorId());
            match.setApprovalAuthorityNote(clause.approvalAuthorityNote());
            String applicability = applicability(clause.policyId(), data.getLoanPurpose());
            match.setApplicability(applicability);
            match.setApplicabilityReason(applicabilityReason(applicability, clause.policyId(), data.getLoanPurpose()));
            List<String> clauseLimitations = limitations(clause.policyId(), clause.clauseNo());
            match.setLimitations(clauseLimitations);
            limitations.addAll(clauseLimitations);
            boolean needsManual = !STATUS_APPLICABLE.equals(applicability)
                    || clause.approvalAuthorityNote() != null
                    || clause.originalText().contains("原则上")
                    || clause.originalText().contains("超过");
            match.setNeedsManualConfirmation(needsManual);
            if (needsManual) {
                pending.add("制度【" + clause.policyName() + "】" + clause.policyVersion() + " "
                        + clause.clauseNo() + "（" + clause.pageNo() + "）：需人工确认适用性与例外情形");
            }
            if (clause.approvalAuthorityNote() != null) {
                approvalNotes.add(clause.approvalAuthorityNote());
                pending.add("审批权限关注点：" + clause.approvalAuthorityNote());
            }
            String conflict = conflict(clause, data.getLoanPurpose());
            if (conflict != null) {
                match.setConflict(conflict);
                conflicts.add(conflict);
            }
            data.getClauseMatches().add(match);
            if (evidenceIds != null && i < evidenceIds.size()) {
                evidenceRefs.add(evidenceRef(evidenceIds.get(i), clause, match));
            }
        }
    }

    private void buildChecklist(PolicyMatchingData data, List<PolicySnapshots.PolicySummary> matched,
                                Set<String> limitations) {
        boolean hasAdmission = matched.stream()
                .anyMatch(policy -> POLICY_ADMISSION.equals(policy.policyId()));
        boolean hasWorkingCapital = matched.stream()
                .anyMatch(policy -> POLICY_WORKING_CAPITAL.equals(policy.policyId()));
        boolean hasPurposeRestriction = limitations.stream().anyMatch(item -> item.contains("不得用于"));
        data.getChecklist().add(checkItem("客户准入", hasAdmission ? STATUS_APPLICABLE : STATUS_NEEDS_MANUAL,
                hasAdmission ? "《对公客户准入管理办法》第五条" : "未检索到客户准入制度，需人工确认"));
        data.getChecklist().add(checkItem("行业准入", STATUS_NEEDS_MANUAL,
                data.getIndustryName() == null || data.getIndustryName().isBlank()
                        ? "未提供行业信息，Mock 制度库无行业准入细则"
                        : "行业准入需按行业授信政策人工确认"));
        data.getChecklist().add(checkItem("产品适用", matched.stream()
                        .anyMatch(policy -> representsProduct(policy, data.getProductCode()))
                        ? STATUS_APPLICABLE : STATUS_NEEDS_MANUAL,
                "制度产品适用范围与申请产品编码比对"));
        data.getChecklist().add(checkItem("用途限制", hasPurposeRestriction ? STATUS_APPLICABLE : STATUS_NEEDS_MANUAL,
                hasWorkingCapital ? "流动资金贷款不得用于固定资产投资、股权投资、房地产及国家限制行业"
                        : "需人工核对用途限制条款"));
        data.getChecklist().add(checkItem("金额与期限限制", STATUS_NEEDS_MANUAL,
                "受托支付起点、期限上限等数值阈值需按现行制度原文核对"));
        data.getChecklist().add(checkItem("担保条件", STATUS_NEEDS_MANUAL,
                "Mock 制度库未收录担保细则，需人工确认担保条件"));
        data.getChecklist().add(checkItem("审批权限", STATUS_NEEDS_MANUAL,
                data.getApprovalAuthorityFocus() == null ? "需人工核对授权文件" : data.getApprovalAuthorityFocus()));
        data.getChecklist().add(checkItem("例外审批", STATUS_NEEDS_MANUAL,
                "例外情形需按现行授权文件人工确认"));
    }

    private boolean representsProduct(PolicySnapshots.PolicySummary policy, String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        return switch (policy.policyId()) {
            case POLICY_WORKING_CAPITAL -> productCode.toUpperCase().contains("WORKING_CAPITAL");
            case POLICY_FIXED_ASSET -> productCode.toUpperCase().contains("FIXED_ASSET");
            case POLICY_ADMISSION -> true;
            default -> false;
        };
    }

    private String applicability(String policyId, String loanPurpose) {
        if (POLICY_ADMISSION.equals(policyId)) {
            return STATUS_APPLICABLE;
        }
        String purpose = loanPurpose == null ? "" : loanPurpose;
        boolean workingCapitalScenario = containsAny(purpose, "原材料", "采购", "周转", "经营", "贸易");
        boolean projectScenario = containsAny(purpose, "建设", "项目", "工程", "设备", "购置");
        if (POLICY_WORKING_CAPITAL.equals(policyId)) {
            return workingCapitalScenario ? STATUS_APPLICABLE : STATUS_NEEDS_MANUAL;
        }
        if (POLICY_FIXED_ASSET.equals(policyId)) {
            return projectScenario ? STATUS_APPLICABLE : STATUS_NOT_APPLICABLE;
        }
        return STATUS_NEEDS_MANUAL;
    }

    private String applicabilityReason(String applicability, String policyId, String loanPurpose) {
        return switch (applicability) {
            case STATUS_APPLICABLE -> "贷款用途【" + dash(loanPurpose) + "】与制度【" + policyId
                    + "】适用场景存在关键词匹配，仅代表检索相关，需人工确认适用性";
            case STATUS_NOT_APPLICABLE -> "贷款用途【" + dash(loanPurpose) + "】未匹配制度【" + policyId
                    + "】适用场景，需人工复核是否确实不适用";
            default -> "制度【" + policyId + "】与当前用途/产品的适用关系无法自动判定，需人工确认";
        };
    }

    private String conflict(PolicySnapshots.Clause clause, String loanPurpose) {
        String purpose = loanPurpose == null ? "" : loanPurpose;
        boolean workingCapitalScenario = containsAny(purpose, "原材料", "采购", "周转", "经营", "贸易");
        boolean projectScenario = containsAny(purpose, "建设", "项目", "工程", "设备", "购置");
        if (!workingCapitalScenario || !projectScenario) {
            return null;
        }
        if (POLICY_WORKING_CAPITAL.equals(clause.policyId()) || POLICY_FIXED_ASSET.equals(clause.policyId())) {
            return "申请用途同时涉及流动资金与固定资产场景，制度适用存在交叉，需人工确认主适用制度与审批权限";
        }
        return null;
    }

    private List<String> limitations(String policyId, String clauseNo) {
        if (POLICY_WORKING_CAPITAL.equals(policyId) && "第九条".equals(clauseNo)) {
            return List.of("不得用于固定资产投资、股权投资、房地产及国家限制行业");
        }
        if (POLICY_WORKING_CAPITAL.equals(policyId) && "第十五条".equals(clauseNo)) {
            return List.of("单笔支付超过 1000 万元需采用受托支付");
        }
        if (POLICY_FIXED_ASSET.equals(policyId) && "第七条".equals(clauseNo)) {
            return List.of("贷款期限一般不超过 10 年");
        }
        if (POLICY_FIXED_ASSET.equals(policyId) && "第十二条".equals(clauseNo)) {
            return List.of("项目资本金比例原则上不低于 20%");
        }
        if (POLICY_ADMISSION.equals(policyId) && "第十一条".equals(clauseNo)) {
            return List.of("关注类客户新增授信需提升一级审批权限");
        }
        return List.of("无额外限制条款（仍需人工核对制度原文）");
    }

    private boolean isCrossScenario(String loanPurpose) {
        String purpose = loanPurpose == null ? "" : loanPurpose;
        return containsAny(purpose, "原材料", "采购", "周转", "经营", "贸易")
                && containsAny(purpose, "建设", "项目", "工程", "设备", "购置");
    }

    private Map<String, Object> searchParams(PolicyMatchingData data, AgentContext context) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        putIfPresent(params, "productCode", data.getProductCode());
        putIfPresent(params, "loanPurpose", data.getLoanPurpose());
        putIfPresent(params, "industryName", data.getIndustryName());
        putIfPresent(params, "applyAmount", data.getApplyAmount());
        return params;
    }

    private void putIfPresent(Map<String, Object> params, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        params.put(key, value);
    }

    private PolicyMatchingData.PolicySummaryItem toSummaryItem(PolicySnapshots.PolicySummary summary) {
        PolicyMatchingData.PolicySummaryItem item = new PolicyMatchingData.PolicySummaryItem();
        item.setPolicyId(summary.policyId());
        item.setPolicyName(summary.policyName());
        item.setPolicyVersion(summary.policyVersion());
        item.setEffectiveDate(summary.effectiveDate());
        item.setStatus(summary.status());
        return item;
    }

    private PolicyMatchingData.PolicyCheckItem checkItem(String item, String status, String basis) {
        PolicyMatchingData.PolicyCheckItem checkItem = new PolicyMatchingData.PolicyCheckItem();
        checkItem.setItem(item);
        checkItem.setStatus(status);
        checkItem.setBasis(basis);
        return checkItem;
    }

    private void addSearchEvidence(List<EvidenceRef> evidenceRefs, ToolResult<PolicySnapshots.SearchResult> result,
                                   PolicyMatchingData data) {
        List<String> evidenceIds = result.getEvidenceIds();
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return;
        }
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(evidenceIds.getFirst());
        ref.setEvidenceType(EvidenceType.POLICY_DOCUMENT.name());
        ref.setSourceSystem(result.getSource());
        ref.setSourceReference("POLICY_SEARCH");
        ref.setDataPeriod(result.getData().dataPeriod());
        ref.setExcerpt("制度检索：" + result.getData().query());
        evidenceRefs.add(ref);
    }

    private EvidenceRef evidenceRef(String evidenceId, PolicySnapshots.Clause clause,
                                    PolicyMatchingData.PolicyClauseMatch match) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(evidenceId);
        ref.setEvidenceType(EvidenceType.POLICY_DOCUMENT.name());
        ref.setSourceSystem("MOCK_POLICY_REGISTRY");
        ref.setSourceReference(clause.vectorId());
        ref.setDataPeriod(match.getEffectiveDate() == null ? null : match.getEffectiveDate().toString());
        ref.setExcerpt(clause.policyName() + " " + clause.policyVersion() + " " + clause.clauseNo()
                + "（" + clause.pageNo() + "）：" + clause.originalText());
        return ref;
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private void warnIfMock(String toolCode, ToolResult<?> toolResult, List<String> warnings) {
        if (toolRegistry.require(toolCode).getDefinition().isMock()) {
            warnings.add("工具【" + toolCode + "】返回 Mock 制度数据（来源 " + toolResult.getSource()
                    + "），结论仅用于链路验证");
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private AgentResult<PolicyMatchingData> result(AgentContext context, PolicyMatchingData data,
                                                   List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                   List<AgentSupplementRequest> supplementRequests,
                                                   List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                   BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<PolicyMatchingData> result = new AgentResult<>();
        result.setAgentCode(AGENT_CODE);
        result.setExecutionId(context.getExecutionId());
        result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
        result.setSummary(summary);
        result.setData(data);
        result.setFindings(findings);
        result.setDataGaps(gaps);
        result.setSupplementRequests(supplementRequests);
        result.setEvidenceRefs(evidenceRefs);
        result.setConfidence(confidence);
        result.setEvidenceCompleteness(completeness);
        result.setWarnings(warnings);
        result.setModelVersion("rule-engine");
        result.setPromptVersion("rule-1.0");
        return result;
    }

    private AgentDataGap dataGap(String dataCode, String dataName, String description, boolean required,
                                 String status, String impact, String suggestedAction) {
        AgentDataGap gap = new AgentDataGap();
        gap.setDataCode(dataCode);
        gap.setDataName(dataName);
        gap.setDescription(description);
        gap.setRequired(required);
        gap.setSourceType(EvidenceType.POLICY_DOCUMENT.name());
        gap.setStatus(status);
        gap.setImpact(impact);
        gap.setSuggestedAction(suggestedAction);
        return gap;
    }

    private AgentSupplementRequest supplement(String dataCode, String dataName, String reason, String action) {
        AgentSupplementRequest request = new AgentSupplementRequest();
        request.setDataCode(dataCode);
        request.setDataName(dataName);
        request.setReason(reason);
        request.setSuggestedAction(action);
        return request;
    }

    private static AgentDefinition buildDefinition() {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode(AGENT_CODE);
        definition.setAgentName("审批政策匹配 Agent");
        definition.setDescription("检索适用制度条款并输出检查清单、限制条款与审批权限关注点，不做合规结论");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("LOAN_PURPOSE_ANALYSIS", "FRAUD_DETECTION"));
        definition.setRequiredTools(List.of(TOOL_SEARCH, TOOL_CLAUSES));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }
}
