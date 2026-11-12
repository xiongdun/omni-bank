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
import com.omnibank.service.agent.dd.plan.DdAgentCatalog;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 综合风险评估 Agent：汇总上游 10 个 Agent 的结论，按 9 个维度去重与关联，
 * 输出评估条件、还款来源、担保、缓释建议与金额期限关注事项；
 * 未提供本行评级标准时不输出风险等级，不输出最终审批金额与合规结论。
 */
@Component
@RequiredArgsConstructor
public class ComprehensiveRiskAssessmentAgent implements CreditAgent<Void, ComprehensiveRiskAssessmentData> {

    public static final String AGENT_CODE = DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT;

    public static final String RISK_LEVEL_NOTE = "未提供本行正式风险评级标准，AI 不输出风险等级";

    public static final String APPROVAL_NOTE = "AI 综合评估不代替最终审批人决策：不输出最终审批金额，不输出合规结论";

    private static final String RISK_LEVEL_NOT_RATED = "NOT_RATED";

    private static final List<String> UPSTREAM_AGENTS = List.of(
            DdAgentCatalog.SUBJECT_VERIFICATION,
            DdAgentCatalog.FINANCIAL_ANALYSIS,
            DdAgentCatalog.JUDICIAL_RISK,
            DdAgentCatalog.CREDIT_DEBT_ANALYSIS,
            DdAgentCatalog.SITE_VISIT,
            DdAgentCatalog.DOCUMENT_RECOGNITION,
            DdAgentCatalog.INDUSTRY_EXPERT,
            DdAgentCatalog.LOAN_PURPOSE_ANALYSIS,
            DdAgentCatalog.FRAUD_DETECTION,
            DdAgentCatalog.POLICY_MATCHING);

    private static final List<String> REQUIRED_AGENTS = List.of(
            DdAgentCatalog.FINANCIAL_ANALYSIS,
            DdAgentCatalog.CREDIT_DEBT_ANALYSIS,
            DdAgentCatalog.LOAN_PURPOSE_ANALYSIS);

    private static final Map<String, String> AGENT_DIMENSIONS = buildAgentDimensions();

    private static final Map<String, String> DIMENSION_NAMES = buildDimensionNames();

    private static final Map<String, String> AGENT_NAMES = buildAgentNames();

    private final DdJsonSupport jsonSupport;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "综合风险评估 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return buildDefinition();
    }

    @Override
    public AgentResult<ComprehensiveRiskAssessmentData> execute(AgentContext context, Void input) {
        List<String> warnings = new ArrayList<>();
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplements = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();

        Map<String, UpstreamAgentResults.Snapshot> snapshots = UpstreamAgentResults.collect(
                context.getUpstreamResults(), UPSTREAM_AGENTS, jsonSupport);
        List<String> missingAgents = UPSTREAM_AGENTS.stream()
                .filter(code -> !snapshots.get(code).succeeded())
                .toList();
        List<String> requiredMissing = REQUIRED_AGENTS.stream()
                .filter(code -> !snapshots.get(code).succeeded())
                .toList();
        boolean assessable = requiredMissing.isEmpty();

        List<RawRisk> rawRisks = collectRisks(snapshots);
        List<MergedRisk> mergedRisks = merge(rawRisks);
        Map<String, EvidenceRef> evidenceIndex = indexEvidence(context);

        ComprehensiveRiskAssessmentData data = new ComprehensiveRiskAssessmentData();
        data.setAssessable(assessable);
        data.setNotAssessableReasons(requiredMissing.stream()
                .map(code -> "缺少关键上游结果：" + AGENT_NAMES.get(code))
                .toList());
        data.setRiskLevel(RISK_LEVEL_NOT_RATED);
        data.setRiskLevelNote(RISK_LEVEL_NOTE);
        data.setApprovalNote(APPROVAL_NOTE);
        data.setDimensions(buildDimensions(context, snapshots, mergedRisks));
        data.setMajorRisks(buildMajorRisks(mergedRisks));
        data.setRiskRelations(buildRelations(mergedRisks));
        data.setRawFindingCount(rawRisks.size());
        data.setDeduplicatedFindingCount(mergedRisks.size());
        data.setPrimaryRepaymentAnalysis(primaryRepaymentAnalysis(snapshots));
        data.setSecondaryRepaymentAnalysis(secondaryRepaymentAnalysis(context));
        data.setGuaranteeAnalysis(guaranteeAnalysis(context));
        data.setMitigationSuggestions(mitigationSuggestions(context, requiredMissing, mergedRisks));
        data.setAmountAndTermConcerns(amountAndTermConcerns(context, snapshots));
        data.setMissingAgents(missingAgents);
        data.setMissingItems(missingItems(missingAgents));
        data.setCompletenessDetail("上游 Agent 成功 " + (UPSTREAM_AGENTS.size() - missingAgents.size())
                + "/" + UPSTREAM_AGENTS.size() + "，关键上游"
                + (assessable ? "齐全" : "缺失：" + joinAgentNames(requiredMissing)));

        BigDecimal completeness = BigDecimal.valueOf(UPSTREAM_AGENTS.size() - missingAgents.size())
                .divide(BigDecimal.valueOf(UPSTREAM_AGENTS.size()), 2, RoundingMode.HALF_UP);
        data.setDataCompleteness(completeness);
        data.setAssessmentConfidence(confidence(assessable, UPSTREAM_AGENTS.size() - missingAgents.size()));

        Set<String> distinctEvidence = new LinkedHashSet<>();
        snapshots.values().forEach(snapshot -> distinctEvidence.addAll(snapshot.evidenceIds()));
        data.setEvidenceCount(distinctEvidence.size());
        for (String evidenceId : distinctEvidence) {
            evidenceRefs.add(evidenceIndex.getOrDefault(evidenceId, minimalRef(evidenceId)));
        }

        addGaps(missingAgents, gaps, supplements);
        addMajorRiskFindings(mergedRisks, evidenceIndex, findings);
        data.setDataGapCount(gaps.size());

        data.setRiskSummary(summary(assessable, requiredMissing, rawRisks.size(), mergedRisks.size(),
                gaps.size()));

        warnings.add(RISK_LEVEL_NOTE);
        warnings.add(APPROVAL_NOTE);
        if (context.isMockMode()) {
            warnings.add("上游分析基于 Mock 证据（isMock=true），综合评估仅用于链路验证");
        }

        BigDecimal evidenceCompleteness = completeness;
        return result(context, data, findings, gaps, supplements, evidenceRefs, warnings,
                data.getAssessmentConfidence(), evidenceCompleteness, data.getRiskSummary());
    }

    private List<RawRisk> collectRisks(Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        List<RawRisk> risks = new ArrayList<>();
        for (String agentCode : UPSTREAM_AGENTS) {
            UpstreamAgentResults.Snapshot snapshot = snapshots.get(agentCode);
            if (!snapshot.succeeded()) {
                continue;
            }
            for (Map<String, Object> finding : snapshot.findings()) {
                RawRisk risk = new RawRisk();
                risk.agentCode = agentCode;
                risk.findingCode = UpstreamAgentResults.dataText(finding, "findingCode");
                risk.category = UpstreamAgentResults.dataText(finding, "category");
                risk.severity = normalizeSeverity(UpstreamAgentResults.dataText(finding, "severity"));
                risk.title = UpstreamAgentResults.dataText(finding, "title");
                risk.description = UpstreamAgentResults.dataText(finding, "description");
                risk.status = UpstreamAgentResults.dataText(finding, "status");
                risk.confidence = decimal(finding.get("confidence"));
                risk.evidenceIds = findingEvidenceIds(finding);
                risks.add(risk);
            }
        }
        return risks;
    }

    private List<MergedRisk> merge(List<RawRisk> rawRisks) {
        int size = rawRisks.size();
        int[] parent = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                if (sameFact(rawRisks.get(i), rawRisks.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            groups.computeIfAbsent(find(parent, i), key -> new ArrayList<>()).add(i);
        }
        List<MergedRisk> merged = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            RawRisk primary = rawRisks.get(group.get(0));
            MergedRisk risk = new MergedRisk();
            risk.primaryAgent = primary.agentCode;
            risk.findingCode = primary.findingCode;
            risk.category = primary.category;
            risk.title = primary.title;
            risk.description = primary.description;
            risk.status = primary.status;
            risk.agents = new ArrayList<>();
            risk.findingCodes = new ArrayList<>();
            Set<String> evidenceIds = new LinkedHashSet<>();
            BigDecimal confidenceSum = BigDecimal.ZERO;
            int confidenceCount = 0;
            String severity = "UNKNOWN";
            for (Integer index : group) {
                RawRisk item = rawRisks.get(index);
                if (!risk.agents.contains(item.agentCode)) {
                    risk.agents.add(item.agentCode);
                }
                if (item.findingCode != null && !risk.findingCodes.contains(item.findingCode)) {
                    risk.findingCodes.add(item.findingCode);
                }
                evidenceIds.addAll(item.evidenceIds);
                severity = maxSeverity(severity, item.severity);
                if (item.confidence != null) {
                    confidenceSum = confidenceSum.add(item.confidence);
                    confidenceCount++;
                }
            }
            risk.severity = severity;
            risk.evidenceIds = List.copyOf(evidenceIds);
            risk.confidence = confidenceCount == 0 ? new BigDecimal("0.60")
                    : confidenceSum.divide(BigDecimal.valueOf(confidenceCount), 2, RoundingMode.HALF_UP);
            merged.add(risk);
        }
        return merged;
    }

    private List<ComprehensiveRiskAssessmentData.DimensionAssessment> buildDimensions(
            AgentContext context, Map<String, UpstreamAgentResults.Snapshot> snapshots,
            List<MergedRisk> mergedRisks) {
        List<ComprehensiveRiskAssessmentData.DimensionAssessment> dimensions = new ArrayList<>();
        for (Map.Entry<String, String> entry : DIMENSION_NAMES.entrySet()) {
            String dimension = entry.getKey();
            List<String> agents = UPSTREAM_AGENTS.stream()
                    .filter(code -> dimension.equals(AGENT_DIMENSIONS.get(code)))
                    .toList();
            ComprehensiveRiskAssessmentData.DimensionAssessment assessment =
                    new ComprehensiveRiskAssessmentData.DimensionAssessment();
            assessment.setDimension(dimension);
            assessment.setDimensionName(entry.getValue());
            assessment.setAgentCodes(agents);
            int succeeded = (int) agents.stream().filter(code -> snapshots.get(code).succeeded()).count();
            assessment.setSucceededAgentCount(succeeded);
            List<MergedRisk> signals = mergedRisks.stream()
                    .filter(risk -> dimension.equals(AGENT_DIMENSIONS.get(risk.primaryAgent)))
                    .toList();
            assessment.setSignalCount(signals.size());
            String highest = "UNKNOWN";
            for (MergedRisk signal : signals) {
                highest = maxSeverity(highest, signal.severity);
            }
            assessment.setHighestSeverity(highest);
            Set<String> evidenceIds = new LinkedHashSet<>();
            agents.forEach(code -> evidenceIds.addAll(snapshots.get(code).evidenceIds()));
            assessment.setEvidenceCount(evidenceIds.size());
            if (agents.isEmpty()) {
                assessment.setStatus(guaranteeType(context) == null ? "UNAVAILABLE" : "PARTIAL");
                assessment.setDescription("担保维度无独立上游 Agent，依据授信申请担保方式与担保资料评估");
            } else {
                assessment.setStatus(succeeded == agents.size() ? "SUFFICIENT"
                        : succeeded > 0 ? "PARTIAL" : "UNAVAILABLE");
                assessment.setDescription("上游 Agent 成功 " + succeeded + "/" + agents.size()
                        + "；去重风险线索 " + signals.size() + " 条；最高等级 " + highest);
            }
            dimensions.add(assessment);
        }
        return dimensions;
    }

    private List<ComprehensiveRiskAssessmentData.MajorRisk> buildMajorRisks(List<MergedRisk> mergedRisks) {
        List<ComprehensiveRiskAssessmentData.MajorRisk> majorRisks = new ArrayList<>();
        for (MergedRisk merged : mergedRisks) {
            if (severityRank(merged.severity) < severityRank("HIGH")) {
                continue;
            }
            ComprehensiveRiskAssessmentData.MajorRisk risk = new ComprehensiveRiskAssessmentData.MajorRisk();
            risk.setFindingCode(merged.findingCode);
            risk.setCategory(merged.category);
            risk.setSeverity(merged.severity);
            risk.setTitle(merged.title);
            risk.setDescription(merged.description);
            risk.setStatus("NEEDS_VERIFICATION");
            risk.setSourceAgents(List.copyOf(merged.agents));
            risk.setRelatedFindingCodes(List.copyOf(merged.findingCodes));
            risk.setEvidenceIds(List.copyOf(merged.evidenceIds));
            majorRisks.add(risk);
        }
        return majorRisks;
    }

    private List<ComprehensiveRiskAssessmentData.RiskRelation> buildRelations(List<MergedRisk> mergedRisks) {
        List<ComprehensiveRiskAssessmentData.RiskRelation> relations = new ArrayList<>();
        for (MergedRisk merged : mergedRisks) {
            if (merged.agents.size() < 2) {
                continue;
            }
            ComprehensiveRiskAssessmentData.RiskRelation relation =
                    new ComprehensiveRiskAssessmentData.RiskRelation();
            relation.setRelationType("CROSS_AGENT_SAME_FACT");
            relation.setAgents(List.copyOf(merged.agents));
            relation.setFindingCodes(List.copyOf(merged.findingCodes));
            relation.setDescription("同一风险事实由 " + String.join("、", merged.agents)
                    + " 共同发现，已合并为 1 条风险，避免重复计数");
            relations.add(relation);
        }
        return relations;
    }

    private void addMajorRiskFindings(List<MergedRisk> mergedRisks, Map<String, EvidenceRef> evidenceIndex,
                                      List<AgentFinding> findings) {
        for (MergedRisk merged : mergedRisks) {
            if (severityRank(merged.severity) < severityRank("HIGH")) {
                continue;
            }
            AgentFinding finding = new AgentFinding();
            finding.setFindingCode(merged.findingCode);
            finding.setCategory(merged.category);
            finding.setSeverity(merged.severity);
            finding.setTitle(merged.title);
            finding.setDescription(merged.description);
            finding.setConfidence(merged.confidence);
            finding.setStatus("NEEDS_VERIFICATION");
            finding.setRelatedFindingCodes(List.copyOf(merged.findingCodes));
            finding.setVerificationSuggestion("由客户经理结合原始证据核实，AI 不将风险线索认定为事实");
            List<EvidenceRef> refs = new ArrayList<>();
            for (String evidenceId : merged.evidenceIds) {
                refs.add(evidenceIndex.getOrDefault(evidenceId, minimalRef(evidenceId)));
            }
            finding.setEvidenceRefs(refs);
            findings.add(finding);
        }
    }

    private String primaryRepaymentAnalysis(Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot financial = snapshots.get(DdAgentCatalog.FINANCIAL_ANALYSIS);
        if (!financial.succeeded()) {
            return "未获取财务分析结果，第一还款来源无法评估";
        }
        BigDecimal operatingCashFlow = latestIndicator(financial.data(), "operatingCashFlow");
        BigDecimal netProfit = latestIndicator(financial.data(), "netProfit");
        if (operatingCashFlow == null && netProfit == null) {
            return "财务分析未返回最新期间经营现金流与净利润，第一还款来源需人工评估";
        }
        return "第一还款来源以经营现金流为主：最新期间经营现金流 "
                + money(operatingCashFlow) + " 元、净利润 " + money(netProfit)
                + " 元（期间口径见财务分析）；AI 仅汇总数据，不判断还款能力是否充足";
    }

    private String secondaryRepaymentAnalysis(AgentContext context) {
        String guaranteeType = guaranteeType(context);
        if (guaranteeType == null) {
            return "授信申请未提供担保方式，第二还款来源无法评估";
        }
        return "第二还款来源依赖担保方式【" + guaranteeType
                + "】；抵质押物估值与保证人代偿能力数据未提供，AI 无法测算担保覆盖，担保不替代第一还款来源";
    }

    private String guaranteeAnalysis(AgentContext context) {
        String guaranteeType = guaranteeType(context);
        return guaranteeType == null
                ? "担保方式缺失：需补充担保方案与担保资料"
                : "担保方式：" + guaranteeType
                        + "；担保物评估价值、抵押率、登记状态与保证人代偿能力需人工核查";
    }

    private List<String> mitigationSuggestions(AgentContext context, List<String> requiredMissing,
                                               List<MergedRisk> mergedRisks) {
        Set<String> suggestions = new LinkedHashSet<>();
        if (!requiredMissing.isEmpty()) {
            suggestions.add("补齐关键上游资料（" + joinAgentNames(requiredMissing) + "）后重新执行分析");
        }
        boolean fraudSignal = mergedRisks.stream()
                .anyMatch(risk -> DdAgentCatalog.FRAUD_DETECTION.equals(risk.primaryAgent));
        if (fraudSignal) {
            suggestions.add("对反欺诈待核实事项开展实地核查，补充合同、发票与银行流水等原始凭证");
        }
        if (guaranteeType(context) == null) {
            suggestions.add("补充担保方案，视情况追加抵押、质押或保证等风险缓释措施");
        }
        if (hasSignal(mergedRisks, DdAgentCatalog.FINANCIAL_ANALYSIS)) {
            suggestions.add("持续监控经营现金流与销售回款，设置还款来源预警");
        }
        if (hasSignal(mergedRisks, DdAgentCatalog.CREDIT_DEBT_ANALYSIS)) {
            suggestions.add("关注存量债务到期分布与对外担保变化，必要时压降敞口");
        }
        suggestions.add("贷后监测：跟踪征信、司法涉诉、关联交易与资金流向变化");
        suggestions.add("以上建议仅供客户经理与审批人参考，不替代审批决策");
        return List.copyOf(suggestions);
    }

    private List<String> amountAndTermConcerns(AgentContext context,
                                               Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        List<String> concerns = new ArrayList<>();
        LoanApplicationVO application = context.getCreditApplication();
        if (application != null && application.getApplyAmount() != null) {
            concerns.add("申请金额 " + application.getApplyAmount() + " 元，申请期限 "
                    + dash(application.getApplyTerm()) + " " + dash(application.getTermUnit()));
        }
        UpstreamAgentResults.Snapshot loanPurpose = snapshots.get(DdAgentCatalog.LOAN_PURPOSE_ANALYSIS);
        if (loanPurpose.succeeded()) {
            Map<String, Object> data = loanPurpose.data();
            concerns.add("测算需求 " + dash(UpstreamAgentResults.dataText(data, "measuredDemand"))
                    + " 元，资金缺口 " + dash(UpstreamAgentResults.dataText(data, "fundingGap"))
                    + " 元（测算口径见贷款用途分析）");
            concerns.add("金额合理性：" + dash(UpstreamAgentResults.dataText(data, "amountReasonableness"))
                    + "；期限合理性：" + dash(UpstreamAgentResults.dataText(data, "termReasonableness")));
            String reasonableness = UpstreamAgentResults.dataText(data, "amountReasonableness");
            if (reasonableness != null && (reasonableness.contains("EXCESSIVE")
                    || reasonableness.contains("SLIGHTLY_HIGH"))) {
                concerns.add("申请金额高于测算需求，需核实用途真实性与资金缺口依据");
            }
        } else {
            concerns.add("贷款用途测算结果缺失，无法输出金额与期限关注事项");
        }
        concerns.add("AI 不输出最终审批金额与期限，最终额度由有权审批人结合担保、风险缓释与授权确定");
        return List.copyOf(concerns);
    }

    private void addGaps(List<String> missingAgents, List<AgentDataGap> gaps,
                         List<AgentSupplementRequest> supplements) {
        for (String agentCode : missingAgents) {
            boolean required = REQUIRED_AGENTS.contains(agentCode);
            gaps.add(dataGap("UPSTREAM_" + agentCode, AGENT_NAMES.get(agentCode),
                    "上游 Agent 未成功执行或结果缺失", required, "UNAVAILABLE",
                    required ? "关键维度缺失，无法形成完整综合评估" : "该维度无法评估",
                    "重新执行该 Agent 或补充对应资料"));
            if (required) {
                supplements.add(supplement("UPSTREAM_" + agentCode, AGENT_NAMES.get(agentCode),
                        "关键上游结果缺失，综合评估不具备完整条件",
                        "补充资料后重新执行该 Agent 并重新汇总"));
            }
        }
        gaps.add(dataGap("GUARANTEE_VALUATION", "担保物估值与代偿能力",
                "未提供抵质押物评估价值、抵押率与保证人代偿能力数据", false, "UNAVAILABLE",
                "无法测算担保覆盖倍数", "补充担保物评估报告、权证或保证人财务资料"));
    }

    private Map<String, EvidenceRef> indexEvidence(AgentContext context) {
        Map<String, EvidenceRef> index = new HashMap<>();
        if (context.getEvidences() != null) {
            for (EvidenceRef ref : context.getEvidences()) {
                if (ref.getEvidenceId() != null) {
                    index.put(ref.getEvidenceId(), ref);
                }
            }
        }
        return index;
    }

    private List<String> findingEvidenceIds(Map<String, Object> finding) {
        Object refs = finding.get("evidenceRefs");
        if (!(refs instanceof List<?> list)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("evidenceId") != null) {
                ids.add(String.valueOf(map.get("evidenceId")));
            }
        }
        return List.copyOf(ids);
    }

    private BigDecimal latestIndicator(Map<String, Object> data, String key) {
        Object periods = data == null ? null : data.get("periodIndicators");
        if (periods instanceof List<?> list && !list.isEmpty() && list.getLast() instanceof Map<?, ?> latest) {
            return decimal(latest.get(key));
        }
        return data == null ? null : decimal(data.get(key));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean sameFact(RawRisk left, RawRisk right) {
        if (!left.evidenceIds.isEmpty() && !right.evidenceIds.isEmpty()
                && left.evidenceIds.stream().anyMatch(right.evidenceIds::contains)) {
            return true;
        }
        return left.category != null && left.category.equals(right.category)
                && normalizeTitle(left.title).equals(normalizeTitle(right.title))
                && !normalizeTitle(left.title).isEmpty();
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private int find(int[] parent, int index) {
        if (parent[index] != index) {
            parent[index] = find(parent, parent[index]);
        }
        return parent[index];
    }

    private void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot;
        }
    }

    private String maxSeverity(String left, String right) {
        return severityRank(right) > severityRank(left) ? right : left;
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

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "UNKNOWN";
        }
        String upper = severity.trim().toUpperCase(Locale.ROOT);
        return List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(upper) ? upper : "UNKNOWN";
    }

    private boolean hasSignal(List<MergedRisk> risks, String agentCode) {
        return risks.stream().anyMatch(risk -> agentCode.equals(risk.primaryAgent));
    }

    private String guaranteeType(AgentContext context) {
        String guaranteeType = context.getCreditApplication() == null ? null
                : context.getCreditApplication().getGuaranteeType();
        return guaranteeType == null || guaranteeType.isBlank() ? null : guaranteeType;
    }

    private BigDecimal confidence(boolean assessable, int succeededAgents) {
        double raw = assessable
                ? Math.min(0.90, 0.50 + 0.04 * succeededAgents)
                : Math.min(0.50, 0.20 + 0.03 * succeededAgents);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> missingItems(List<String> missingAgents) {
        List<String> items = new ArrayList<>(joinAgentNames(missingAgents));
        items.add("抵质押物估值、抵押率与保证人代偿能力数据");
        return List.copyOf(items);
    }

    private List<String> joinAgentNames(List<String> agentCodes) {
        return agentCodes.stream().map(code -> AGENT_NAMES.getOrDefault(code, code)).toList();
    }

    private String summary(boolean assessable, List<String> requiredMissing, int rawCount, int dedupCount,
                           int gapCount) {
        String head = assessable
                ? "综合风险评估完成：上游关键结果齐全"
                : "暂不具备完整评估条件：缺少关键上游结果（" + String.join("、", joinAgentNames(requiredMissing)) + "）";
        return head + "；风险线索 " + rawCount + " 条，去重后 " + dedupCount + " 条；数据缺口 " + gapCount
                + " 项；" + RISK_LEVEL_NOTE;
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

    private EvidenceRef minimalRef(String evidenceId) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(evidenceId);
        return ref;
    }

    private String money(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String dash(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value);
    }

    private AgentResult<ComprehensiveRiskAssessmentData> result(
            AgentContext context, ComprehensiveRiskAssessmentData data, List<AgentFinding> findings,
            List<AgentDataGap> gaps, List<AgentSupplementRequest> supplements, List<EvidenceRef> evidenceRefs,
            List<String> warnings, BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<ComprehensiveRiskAssessmentData> result = new AgentResult<>();
        result.setAgentCode(AGENT_CODE);
        result.setExecutionId(context.getExecutionId());
        result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
        result.setSummary(summary);
        result.setData(data);
        result.setFindings(findings);
        result.setDataGaps(gaps);
        result.setSupplementRequests(supplements);
        result.setEvidenceRefs(evidenceRefs);
        result.setConfidence(confidence);
        result.setEvidenceCompleteness(completeness);
        result.setWarnings(warnings);
        result.setModelVersion("rule-engine");
        result.setPromptVersion("rule-1.0");
        return result;
    }

    private static Map<String, String> buildAgentDimensions() {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put(DdAgentCatalog.SUBJECT_VERIFICATION, "SUBJECT");
        dimensions.put(DdAgentCatalog.DOCUMENT_RECOGNITION, "SUBJECT");
        dimensions.put(DdAgentCatalog.FINANCIAL_ANALYSIS, "FINANCIAL");
        dimensions.put(DdAgentCatalog.CREDIT_DEBT_ANALYSIS, "CREDIT");
        dimensions.put(DdAgentCatalog.JUDICIAL_RISK, "JUDICIAL");
        dimensions.put(DdAgentCatalog.SITE_VISIT, "OPERATION");
        dimensions.put(DdAgentCatalog.INDUSTRY_EXPERT, "OPERATION");
        dimensions.put(DdAgentCatalog.LOAN_PURPOSE_ANALYSIS, "PURPOSE");
        dimensions.put(DdAgentCatalog.FRAUD_DETECTION, "FRAUD");
        dimensions.put(DdAgentCatalog.POLICY_MATCHING, "POLICY");
        return Map.copyOf(dimensions);
    }

    private static Map<String, String> buildDimensionNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("SUBJECT", "主体");
        names.put("OPERATION", "经营");
        names.put("FINANCIAL", "财务");
        names.put("CREDIT", "信用");
        names.put("JUDICIAL", "司法");
        names.put("FRAUD", "欺诈");
        names.put("PURPOSE", "用途");
        names.put("GUARANTEE", "担保");
        names.put("POLICY", "政策");
        return java.util.Collections.unmodifiableMap(names);
    }

    private static Map<String, String> buildAgentNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(DdAgentCatalog.SUBJECT_VERIFICATION, "主体核验");
        names.put(DdAgentCatalog.FINANCIAL_ANALYSIS, "财务分析");
        names.put(DdAgentCatalog.JUDICIAL_RISK, "司法风险");
        names.put(DdAgentCatalog.CREDIT_DEBT_ANALYSIS, "征信债务");
        names.put(DdAgentCatalog.SITE_VISIT, "现场调查");
        names.put(DdAgentCatalog.DOCUMENT_RECOGNITION, "材料识别");
        names.put(DdAgentCatalog.INDUSTRY_EXPERT, "行业专家");
        names.put(DdAgentCatalog.LOAN_PURPOSE_ANALYSIS, "贷款用途");
        names.put(DdAgentCatalog.FRAUD_DETECTION, "反欺诈");
        names.put(DdAgentCatalog.POLICY_MATCHING, "政策匹配");
        return Map.copyOf(names);
    }

    private static AgentDefinition buildDefinition() {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode(AGENT_CODE);
        definition.setAgentName("综合风险评估 Agent");
        definition.setDescription("汇总上游 10 个 Agent 结论，按 9 个维度去重、关联并输出缓释建议；不输出风险等级与最终审批金额");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(UPSTREAM_AGENTS);
        definition.setRequiredTools(List.of());
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    /** 上游原始风险线索 */
    private static final class RawRisk {

        private String agentCode;

        private String findingCode;

        private String category;

        private String severity;

        private String title;

        private String description;

        private String status;

        private BigDecimal confidence;

        private List<String> evidenceIds = List.of();
    }

    /** 去重后的风险线索 */
    private static final class MergedRisk {

        private String primaryAgent;

        private String findingCode;

        private String category;

        private String severity;

        private String title;

        private String description;

        private String status;

        private List<String> agents = List.of();

        private List<String> findingCodes = List.of();

        private List<String> evidenceIds = List.of();

        private BigDecimal confidence;
    }
}
