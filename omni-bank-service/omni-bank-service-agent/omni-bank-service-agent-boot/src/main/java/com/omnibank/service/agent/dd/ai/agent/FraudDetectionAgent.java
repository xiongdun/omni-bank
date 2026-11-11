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
import com.omnibank.service.agent.dd.tool.impl.FraudCheckSnapshots;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 09 反欺诈：覆盖主体、关联、财务、交易、资金、材料等维度，规则命中只输出待核实线索，
 * 资金回流、金额相近、时间接近不作为欺诈认定，人工核查结果由人工回填。
 */
@Component
@RequiredArgsConstructor
public class FraudDetectionAgent implements CreditAgent<Void, FraudDetectionData> {

    public static final String AGENT_CODE = "FRAUD_DETECTION";

    private static final String TOOL_IDENTITY = "fraud.checkIdentityConsistency";

    private static final String TOOL_RELATED_PARTY = "fraud.checkRelatedPartyTransactions";

    private static final String TOOL_FINANCIAL = "fraud.checkFinancialAnomalies";

    private static final String TOOL_CONTRACT = "fraud.checkContractAnomalies";

    private static final String TOOL_FUND_FLOW = "fraud.checkFundFlowAnomalies";

    private static final String STATUS_NEEDS_VERIFICATION = "NEEDS_VERIFICATION";

    private static final List<String> UPSTREAM_AGENTS = List.of("SUBJECT_VERIFICATION",
            "FINANCIAL_ANALYSIS", "JUDICIAL_RISK", "CREDIT_DEBT_ANALYSIS", "SITE_VISIT",
            "DOCUMENT_RECOGNITION", "LOAN_PURPOSE_ANALYSIS");

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "反欺诈 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<FraudDetectionData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        List<FraudDetectionData.RuleRecord> records = new ArrayList<>();
        FraudDetectionData data = new FraudDetectionData();
        data.setRuleRecords(records);
        data.setIdentityAnomalies(new ArrayList<>());
        data.setRelatedPartyAnomalies(new ArrayList<>());
        data.setFinancialAnomalies(new ArrayList<>());
        data.setTransactionAnomalies(new ArrayList<>());
        data.setFundFlowAnomalies(new ArrayList<>());
        data.setMaterialAnomalies(new ArrayList<>());
        data.setRelationPaths(new ArrayList<>());
        data.setReasonableCauses(new ArrayList<>());
        data.setPendingVerificationItems(new ArrayList<>());
        data.setInvestigationSuggestions(new ArrayList<>());
        data.setHighestSeverity("NONE");

        if (context.isMockMode()) {
            warnings.add("当前证据来自 Mock Adapter（isMock=true），结论仅用于链路验证");
        }
        warnings.add("反欺诈规则命中均为待核实线索，不得直接认定为欺诈，人工核查结果由人工回填");

        String creditCode = resolveCreditCode(context);
        if (creditCode == null) {
            gaps.add(dataGap("FRAUD_BASE_DATA", "反欺诈基础数据",
                    "缺少统一社会信用代码，无法开展反欺诈规则核验", true, "NOT_PROVIDED",
                    "无法识别主体、关联、财务与资金异常", "补充企业统一社会信用代码后重新分析"));
            supplementRequests.add(supplement("FRAUD_BASE_DATA", "反欺诈基础数据",
                    "缺少企业统一社会信用代码", "补充企业统一社会信用代码与授信申请信息"));
            missingItems.add("未获取企业统一社会信用代码，反欺诈分析未完成");
            data.setMissingItems(missingItems);
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), BigDecimal.ZERO, "反欺诈分析未完成：缺少企业统一社会信用代码");
        }

        ToolRequest toolRequest = new ToolRequest(context.getTaskId(), toolParams(context, creditCode));
        int succeededTools = 0;

        ToolResult<FraudCheckSnapshots.IdentityResult> identityResult = call(TOOL_IDENTITY, toolRequest);
        if (identityResult.isSuccess() && identityResult.getData() != null) {
            succeededTools++;
            EvidenceRef ref = addToolEvidenceRef(evidenceRefs, identityResult, EvidenceType.ENTERPRISE_PROFILE,
                    "FRAUD_IDENTITY", identityResult.getData().dataPeriod(), "主体一致性核验");
            collectHits(identityResult.getData().hits(), ref, data, findings);
        } else {
            gaps.add(dataGap(TOOL_IDENTITY, "主体一致性核验", "工具未返回结果", false, "UNAVAILABLE",
                    "主体异常线索可能缺失", "重新执行主体一致性核验"));
        }

        ToolResult<FraudCheckSnapshots.RelatedPartyResult> relatedResult = call(TOOL_RELATED_PARTY, toolRequest);
        if (relatedResult.isSuccess() && relatedResult.getData() != null) {
            succeededTools++;
            FraudCheckSnapshots.RelatedPartyResult payload = relatedResult.getData();
            EvidenceRef ref = addToolEvidenceRef(evidenceRefs, relatedResult, EvidenceType.ENTERPRISE_PROFILE,
                    "FRAUD_RELATED_PARTY", payload.dataPeriod(), "关联关系核验");
            collectHits(payload.hits(), ref, data, findings);
            data.getRelationPaths().addAll(payload.relationPaths());
        } else {
            gaps.add(dataGap(TOOL_RELATED_PARTY, "关联关系核验", "工具未返回结果", false, "UNAVAILABLE",
                    "关联关系异常线索可能缺失", "重新执行关联关系核验"));
        }

        ToolResult<FraudCheckSnapshots.FinancialResult> financialResult = call(TOOL_FINANCIAL, toolRequest);
        if (financialResult.isSuccess() && financialResult.getData() != null) {
            succeededTools++;
            FraudCheckSnapshots.FinancialResult payload = financialResult.getData();
            EvidenceRef ref = addToolEvidenceRef(evidenceRefs, financialResult, EvidenceType.FINANCIAL_STATEMENT,
                    "FRAUD_FINANCIAL", payload.dataPeriod(), "财务异常核验");
            collectHits(payload.hits(), ref, data, findings);
            data.getReasonableCauses().addAll(payload.reasonableCauses());
        } else {
            gaps.add(dataGap(TOOL_FINANCIAL, "财务异常核验", "工具未返回结果", false, "UNAVAILABLE",
                    "财务异常线索可能缺失", "重新执行财务异常核验"));
        }

        ToolResult<FraudCheckSnapshots.ContractResult> contractResult = call(TOOL_CONTRACT, toolRequest);
        if (contractResult.isSuccess() && contractResult.getData() != null) {
            succeededTools++;
            FraudCheckSnapshots.ContractResult payload = contractResult.getData();
            EvidenceRef ref = addToolEvidenceRef(evidenceRefs, contractResult, EvidenceType.CUSTOMER_DOCUMENT,
                    "FRAUD_CONTRACT", payload.dataPeriod(), "合同材料异常核验");
            collectHits(payload.hits(), ref, data, findings);
        } else {
            gaps.add(dataGap(TOOL_CONTRACT, "合同材料异常核验", "工具未返回结果", false, "UNAVAILABLE",
                    "材料与合同异常线索可能缺失", "重新执行合同材料异常核验"));
        }

        ToolResult<FraudCheckSnapshots.FundFlowResult> fundFlowResult = call(TOOL_FUND_FLOW, toolRequest);
        if (fundFlowResult.isSuccess() && fundFlowResult.getData() != null) {
            succeededTools++;
            FraudCheckSnapshots.FundFlowResult payload = fundFlowResult.getData();
            EvidenceRef ref = addToolEvidenceRef(evidenceRefs, fundFlowResult, EvidenceType.CALCULATION,
                    "FRAUD_FUND_FLOW", payload.dataPeriod(), "资金流向异常核验");
            collectHits(payload.hits(), ref, data, findings);
            data.getRelationPaths().addAll(payload.paths());
        } else {
            gaps.add(dataGap(TOOL_FUND_FLOW, "资金流向异常核验", "工具未返回结果", false, "UNAVAILABLE",
                    "资金回流与异常转账线索可能缺失", "重新执行资金流向异常核验"));
        }

        checkUpstream(context, gaps, missingItems);

        data.setRuleHitCount(records.size());
        data.setHighestSeverity(highestSeverity(records));
        data.setRiskImpact(records.isEmpty()
                ? "未命中反欺诈规则，仍需以人工核查与实地调查为准"
                : "命中 " + records.size() + " 条规则，最高严重程度 " + data.getHighestSeverity()
                        + "；全部为待核实线索，不构成欺诈认定");
        data.setPendingVerificationItems(data.getPendingVerificationItems().stream().distinct().toList());
        data.setInvestigationSuggestions(data.getInvestigationSuggestions().stream().distinct().toList());
        data.setMissingItems(missingItems);
        data.setEvidenceCount(evidenceRefs.size());

        BigDecimal completeness = BigDecimal.valueOf(succeededTools)
                .divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = succeededTools == 5 ? new BigDecimal("0.82") : new BigDecimal("0.70");
        String summary = "反欺诈分析完成：命中规则 " + records.size() + " 条，最高严重程度 "
                + data.getHighestSeverity() + "，待核实事项 " + data.getPendingVerificationItems().size()
                + " 项，数据缺口 " + gaps.size() + " 项；所有命中均为线索，不构成欺诈认定";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    private void collectHits(List<FraudCheckSnapshots.RuleHit> hits, EvidenceRef evidenceRef,
                             FraudDetectionData data, List<AgentFinding> findings) {
        for (FraudCheckSnapshots.RuleHit hit : hits) {
            FraudDetectionData.RuleRecord record = new FraudDetectionData.RuleRecord();
            record.setRuleCode(hit.ruleCode());
            record.setRuleVersion(hit.ruleVersion());
            record.setCategory(hit.category());
            record.setSeverity(hit.severity());
            record.setTitle(hit.title());
            record.setHitData(hit.hitData());
            record.setDescription(hit.description());
            record.setSuggestion(hit.suggestion());
            record.setHitTime(LocalDateTime.now());
            record.setManualVerificationResult(null);
            record.setStatus(STATUS_NEEDS_VERIFICATION);
            record.setEvidenceRefs(evidenceRef == null ? List.of() : List.of(evidenceRef));
            data.getRuleRecords().add(record);

            String label = "【" + hit.ruleCode() + "】" + hit.title();
            switch (hit.category()) {
                case "IDENTITY" -> data.getIdentityAnomalies().add(label);
                case "RELATED_PARTY" -> data.getRelatedPartyAnomalies().add(label);
                case "FINANCIAL" -> data.getFinancialAnomalies().add(label);
                case "TRANSACTION" -> data.getTransactionAnomalies().add(label);
                case "FUND_FLOW" -> data.getFundFlowAnomalies().add(label);
                case "MATERIAL" -> data.getMaterialAnomalies().add(label);
                default -> data.getTransactionAnomalies().add(label);
            }
            data.getPendingVerificationItems().add(label + "：" + hit.description());
            if (hit.suggestion() != null && !hit.suggestion().isBlank()) {
                data.getInvestigationSuggestions().add(hit.suggestion());
            }
            findings.add(finding(hit.ruleCode(), hit.category(), hit.severity(), hit.title(),
                    hit.hitData() + "；" + hit.description() + "；仅作为待核实线索，不直接认定为欺诈",
                    evidenceRef == null ? List.of() : List.of(evidenceRef), hit.suggestion()));
        }
    }

    private void checkUpstream(AgentContext context, List<AgentDataGap> gaps, List<String> missingItems) {
        Map<String, Object> upstream = context.getUpstreamResults();
        List<String> missing = new ArrayList<>();
        for (String agentCode : UPSTREAM_AGENTS) {
            Object value = upstream == null ? null : upstream.get(agentCode);
            if (value == null) {
                missing.add(agentCode);
                continue;
            }
            if (value instanceof AgentResult<?> agentResult
                    && !AgentExecutionStatus.SUCCEEDED.name().equals(agentResult.getStatus())) {
                missing.add(agentCode);
            }
        }
        if (!missing.isEmpty()) {
            gaps.add(dataGap("UPSTREAM_AGENT_RESULTS", "上游 Agent 结果",
                    "未获取上游结果：" + String.join("、", missing), false, "NOT_PROVIDED",
                    "跨来源交叉核验受限，反欺诈覆盖度下降", "等待上游 Agent 完成后增量重跑反欺诈分析"));
            missingItems.add("上游结果缺失：" + String.join("、", missing));
        }
    }

    private String highestSeverity(List<FraudDetectionData.RuleRecord> records) {
        String highest = "NONE";
        for (FraudDetectionData.RuleRecord record : records) {
            if (severityRank(record.getSeverity()) > severityRank(highest)) {
                highest = record.getSeverity();
            }
        }
        return highest;
    }

    private int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private Map<String, Object> toolParams(AgentContext context, String creditCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("creditCode", creditCode);
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        if (context.getEnterpriseProfile() != null) {
            putIfPresent(params, "enterpriseName", context.getEnterpriseProfile().getEnterpriseName());
            putIfPresent(params, "industryName", context.getEnterpriseProfile().getIndustryName());
        }
        LoanApplicationVO application = context.getCreditApplication();
        if (application != null) {
            putIfPresent(params, "productCode", application.getProductCode());
            putIfPresent(params, "loanPurpose", application.getLoanPurpose());
            putIfPresent(params, "applyAmount", application.getApplyAmount());
        }
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

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private EvidenceRef addToolEvidenceRef(List<EvidenceRef> evidenceRefs, ToolResult<?> toolResult,
                                           EvidenceType evidenceType, String sourceReference, String dataPeriod,
                                           String excerpt) {
        List<String> evidenceIds = toolResult.getEvidenceIds();
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return null;
        }
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(evidenceIds.getFirst());
        ref.setEvidenceType(evidenceType.name());
        ref.setSourceSystem(toolResult.getSource());
        ref.setSourceReference(sourceReference);
        ref.setDataPeriod(dataPeriod);
        ref.setExcerpt(excerpt);
        evidenceRefs.add(ref);
        return ref;
    }

    private String resolveCreditCode(AgentContext context) {
        String creditCode = context.getEnterpriseProfile() == null
                ? null : context.getEnterpriseProfile().getCreditCode();
        return creditCode == null || creditCode.isBlank() ? null : creditCode.trim();
    }

    private AgentResult<FraudDetectionData> result(AgentContext context, FraudDetectionData data,
                                                   List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                   List<AgentSupplementRequest> supplementRequests,
                                                   List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                   BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<FraudDetectionData> result = new AgentResult<>();
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

    private AgentFinding finding(String code, String category, String severity, String title, String description,
                                 List<EvidenceRef> evidenceRefs, String verificationSuggestion) {
        AgentFinding finding = new AgentFinding();
        finding.setFindingCode(code);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setDescription(description);
        finding.setStatus(STATUS_NEEDS_VERIFICATION);
        finding.setConfidence(new BigDecimal("0.70"));
        finding.setEvidenceRefs(List.copyOf(evidenceRefs));
        finding.setRelatedFindingCodes(List.of());
        finding.setVerificationSuggestion(verificationSuggestion);
        return finding;
    }

    private AgentDataGap dataGap(String dataCode, String dataName, String description, boolean required,
                                 String status, String impact, String suggestedAction) {
        AgentDataGap gap = new AgentDataGap();
        gap.setDataCode(dataCode);
        gap.setDataName(dataName);
        gap.setDescription(description);
        gap.setRequired(required);
        gap.setSourceType(EvidenceType.CALCULATION.name());
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
        definition.setAgentName("反欺诈 Agent");
        definition.setDescription("覆盖主体、关联、财务、交易、资金、材料等维度的反欺诈规则核验，命中结果仅作为待核实线索");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("SUBJECT_VERIFICATION", "FINANCIAL_ANALYSIS", "JUDICIAL_RISK",
                "CREDIT_DEBT_ANALYSIS", "SITE_VISIT", "DOCUMENT_RECOGNITION", "LOAN_PURPOSE_ANALYSIS"));
        definition.setRequiredTools(List.of(TOOL_IDENTITY, TOOL_RELATED_PARTY, TOOL_FINANCIAL,
                TOOL_CONTRACT, TOOL_FUND_FLOW));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }
}
