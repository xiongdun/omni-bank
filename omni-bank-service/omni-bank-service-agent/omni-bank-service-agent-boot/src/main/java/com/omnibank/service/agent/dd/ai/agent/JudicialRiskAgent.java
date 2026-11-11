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
import com.omnibank.service.agent.dd.dto.enterprise.EnterpriseVO;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.impl.AdministrativePenaltiesSnapshot;
import com.omnibank.service.agent.dd.tool.impl.EnforcementRecordsSnapshot;
import com.omnibank.service.agent.dd.tool.impl.JudicialRecordsSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 03 司法风险：基于确定性规则分析涉诉、被执行与行政处罚记录。
 * 当前不调用大模型，结论均来自工具取数与规则计算；Mock 数据只用于链路验证。
 */
@Component
@RequiredArgsConstructor
public class JudicialRiskAgent implements CreditAgent<JudicialRiskInput, JudicialRiskData> {

    public static final String AGENT_CODE = "JUDICIAL_RISK";

    private static final String TOOL_JUDICIAL_RECORDS = "judicial.getJudicialRecords";

    private static final String TOOL_ENFORCEMENT_RECORDS = "judicial.getEnforcementRecords";

    private static final String TOOL_ADMIN_PENALTIES = "judicial.getAdministrativePenalties";

    /** 重大诉讼金额占申请金额阈值 */
    private static final BigDecimal MAJOR_LITIGATION_THRESHOLD = new BigDecimal("0.10");

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "司法风险 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<JudicialRiskData> execute(AgentContext context, JudicialRiskInput input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JudicialRiskData data = new JudicialRiskData();

        EnterpriseVO profile = context.getEnterpriseProfile();
        String enterpriseName = firstNonBlank(input == null ? null : input.enterpriseName(),
                profile == null ? null : profile.getEnterpriseName());
        String creditCode = firstNonBlank(input == null ? null : input.creditCode(),
                profile == null ? null : profile.getCreditCode());

        ToolResult<JudicialRecordsSnapshot> lawsuitResult = call(TOOL_JUDICIAL_RECORDS, context,
                enterpriseName, creditCode);
        ToolResult<EnforcementRecordsSnapshot> enforcementResult = call(TOOL_ENFORCEMENT_RECORDS, context,
                enterpriseName, creditCode);
        ToolResult<AdministrativePenaltiesSnapshot> penaltyResult = call(TOOL_ADMIN_PENALTIES, context,
                enterpriseName, creditCode);

        if (context.isMockMode() || isMock(lawsuitResult) || isMock(enforcementResult) || isMock(penaltyResult)) {
            warnings.add("司法数据来自 Mock 数据源（来源系统以 MOCK_ 开头），结论仅用于链路验证");
        }

        JudicialRecordsSnapshot lawsuitSnapshot = lawsuitResult.getData();
        EnforcementRecordsSnapshot enforcementSnapshot = enforcementResult.getData();
        AdministrativePenaltiesSnapshot penaltySnapshot = penaltyResult.getData();

        List<JudicialRecordsSnapshot.JudicialCase> cases = lawsuitSnapshot == null
                ? List.of() : lawsuitSnapshot.cases();
        List<EnforcementRecordsSnapshot.EnforcementRecord> enforcements = enforcementSnapshot == null
                ? List.of() : enforcementSnapshot.records();
        List<AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord> penalties = penaltySnapshot == null
                ? List.of() : penaltySnapshot.records();

        if (lawsuitSnapshot != null) {
            addEvidenceRef(evidenceRefs, lawsuitSnapshot.evidenceId(), lawsuitSnapshot.sourceSystem(),
                    lawsuitSnapshot.sourceReference(), lawsuitSnapshot.dataPeriod(), lawsuitSnapshot.contentSummary());
        }
        if (cases.isEmpty()) {
            gaps.add(dataGap("JUDICIAL_RECORD_MISSING", "司法涉诉记录", "未获取到司法涉诉查询结果", true, "UNAVAILABLE",
                    "无法判断企业涉诉情况与被告未结案件金额", "重新查询司法涉诉数据或要求客户提供诉讼情况说明"));
            supplementRequests.add(supplement("JUDICIAL_RECORD_MISSING", "司法涉诉记录",
                    "司法风险分析缺少涉诉记录", "补充司法涉诉查询结果或客户诉讼情况说明"));
        }
        if (enforcementSnapshot != null) {
            addEvidenceRef(evidenceRefs, enforcementSnapshot.evidenceId(), enforcementSnapshot.sourceSystem(),
                    enforcementSnapshot.sourceReference(), enforcementSnapshot.dataPeriod(),
                    enforcementSnapshot.contentSummary());
        }
        if (enforcements.isEmpty()) {
            gaps.add(dataGap("ENFORCEMENT_RECORD_MISSING", "被执行人记录", "未获取到执行类查询结果", true, "UNAVAILABLE",
                    "无法判断企业是否存在被执行、失信或限制高消费情形", "重新查询执行信息公开数据"));
            supplementRequests.add(supplement("ENFORCEMENT_RECORD_MISSING", "被执行人记录",
                    "司法风险分析缺少执行类记录", "补充执行信息公开查询结果"));
        }
        if (penaltySnapshot != null) {
            addEvidenceRef(evidenceRefs, penaltySnapshot.evidenceId(), penaltySnapshot.sourceSystem(),
                    penaltySnapshot.sourceReference(), penaltySnapshot.dataPeriod(), penaltySnapshot.contentSummary());
        }

        List<Boolean> subjectMatches = new ArrayList<>();
        if (lawsuitSnapshot != null) {
            subjectMatches.add(lawsuitSnapshot.subjectMatchExact());
        }
        if (enforcementSnapshot != null) {
            subjectMatches.add(enforcementSnapshot.subjectMatchExact());
        }
        if (penaltySnapshot != null) {
            subjectMatches.add(penaltySnapshot.subjectMatchExact());
        }
        boolean subjectMatchExact = subjectMatches.stream().allMatch(Boolean::booleanValue);
        String matchedSubject = firstNonBlank(
                lawsuitSnapshot == null ? null : lawsuitSnapshot.matchedSubjectName(),
                enforcementSnapshot == null ? null : enforcementSnapshot.matchedSubjectName(),
                penaltySnapshot == null ? null : penaltySnapshot.matchedSubjectName());
        if (!subjectMatchExact) {
            findings.add(finding("JUD_SUBJECT_MATCH_UNCERTAIN", "SUBJECT_MATCH", "MEDIUM", "司法记录主体匹配待核实",
                    "司法记录主体名称与申报企业【" + safe(enterpriseName) + "】非精确匹配，命中主体【"
                            + safe(matchedSubject) + "】；不能仅凭名称模糊命中形成风险结论",
                    "NEEDS_VERIFICATION", new BigDecimal("0.60"), evidenceRefs,
                    "核对裁判文书当事人统一社会信用代码后确认是否属于同一主体"));
            gaps.add(dataGap("JUD_SUBJECT_MATCH_UNCERTAIN", "司法记录主体匹配依据",
                    "司法记录主体名称非精确匹配，命中主体可能与申报主体不同", true, "UNKNOWN",
                    "案件、执行与处罚记录可能归属其他主体，风险结论存在误判风险", "人工核实主体匹配依据"));
        }

        List<EnforcementRecordsSnapshot.EnforcementRecord> executed = enforcements.stream()
                .filter(record -> "被执行人".equals(record.recordType())).toList();
        BigDecimal enforcementAmount = sumAmounts(executed.stream()
                .map(EnforcementRecordsSnapshot.EnforcementRecord::amount).toList());
        if (!executed.isEmpty()) {
            findings.add(finding("JUD_ENFORCEMENT_EXISTS", "ENFORCEMENT", "HIGH", "存在被执行人记录",
                    "被执行人记录 " + executed.size() + " 条，执行标的合计 " + money(enforcementAmount)
                            + " 元，需核实执行进展与履行能力",
                    "CONFIRMED", new BigDecimal("0.90"), evidenceRefs,
                    "核实执行案件进展、已履行金额与对企业现金流的影响"));
        }

        List<EnforcementRecordsSnapshot.EnforcementRecord> dishonest = enforcements.stream()
                .filter(record -> "失信被执行人".equals(record.recordType())).toList();
        if (!dishonest.isEmpty()) {
            findings.add(finding("JUD_DISHONEST_EXISTS", "ENFORCEMENT", "CRITICAL", "存在失信被执行人记录",
                    "失信被执行人记录 " + dishonest.size() + " 条，反映企业履约意愿与信用状况异常",
                    "CONFIRMED", new BigDecimal("0.95"), evidenceRefs,
                    "核实失信记录形成原因、是否已履行及解除情况"));
        }

        List<JudicialRecordsSnapshot.JudicialCase> pendingDefendantCases = cases.stream()
                .filter(item -> "被告".equals(item.role()) && "审理中".equals(item.status())).toList();
        BigDecimal pendingDefendantAmount = sumAmounts(pendingDefendantCases.stream()
                .map(JudicialRecordsSnapshot.JudicialCase::amount).toList());

        BigDecimal applyAmount = context.getCreditApplication() == null
                ? null : context.getCreditApplication().getApplyAmount();
        BigDecimal pendingDefendantAmountRatio = ratio(pendingDefendantAmount, applyAmount);
        BigDecimal enforcementAmountRatio = ratio(enforcementAmount, applyAmount);
        if (applyAmount == null || applyAmount.signum() <= 0) {
            gaps.add(dataGap("APPLY_AMOUNT_MISSING", "授信申请金额", "上下文缺少授信申请金额或申请金额非正数",
                    true, "NOT_PROVIDED", "无法计算司法金额占申请金额比例", "补充授信申请金额"));
        } else if (pendingDefendantAmount.compareTo(applyAmount.multiply(MAJOR_LITIGATION_THRESHOLD)) > 0) {
            findings.add(finding("JUD_MAJOR_LITIGATION", "LITIGATION", "HIGH", "被告未结案件金额超过申请金额 10%",
                    "被告未结案件 " + pendingDefendantCases.size() + " 件，金额合计 "
                            + money(pendingDefendantAmount) + " 元，占申请金额 "
                            + ratioText(pendingDefendantAmountRatio) + "（阈值 10%）",
                    "CONFIRMED", new BigDecimal("0.85"), evidenceRefs,
                    "核实案件判决可能性、预计赔付金额及对偿债能力的影响"));
        }

        List<AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord> unrectifiedPenalties = penalties.stream()
                .filter(record -> !record.rectified()).toList();
        BigDecimal penaltyAmount = sumAmounts(penalties.stream()
                .map(AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord::amount).toList());
        if (!unrectifiedPenalties.isEmpty()) {
            findings.add(finding("JUD_ADMIN_PENALTY", "PENALTY", "MEDIUM", "存在未整改行政处罚",
                    "未整改行政处罚 " + unrectifiedPenalties.size() + " 条，处罚金额合计 "
                            + money(sumAmounts(unrectifiedPenalties.stream()
                            .map(AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord::amount).toList()))
                            + " 元，需核实整改情况与合规风险",
                    "CONFIRMED", new BigDecimal("0.85"), evidenceRefs,
                    "取得整改证明材料并核实处罚是否影响经营资质"));
        }

        int plaintiffCount = (int) cases.stream().filter(item -> "原告".equals(item.role())).count();
        int defendantCount = (int) cases.stream().filter(item -> "被告".equals(item.role())).count();
        int thirdPartyCount = (int) cases.stream().filter(item -> "第三人".equals(item.role())).count();

        data.setEnterpriseName(enterpriseName);
        data.setCreditCode(creditCode);
        data.setSubjectMatchExact(subjectMatchExact);
        data.setSubjectMatchMode(subjectMatchExact ? "EXACT" : "FUZZY");
        data.setMatchedSubjectName(matchedSubject);
        data.setLawsuitCount(cases.size());
        data.setPlaintiffCount(plaintiffCount);
        data.setDefendantCount(defendantCount);
        data.setThirdPartyCount(thirdPartyCount);
        data.setPendingDefendantCaseCount(pendingDefendantCases.size());
        data.setPendingDefendantAmount(pendingDefendantAmount);
        data.setEnforcementAmount(enforcementAmount);
        data.setPenaltyAmount(penaltyAmount);
        data.setApplyAmount(applyAmount);
        data.setPendingDefendantAmountRatio(pendingDefendantAmountRatio);
        data.setEnforcementAmountRatio(enforcementAmountRatio);
        data.setPenaltyAmountRatio(ratio(penaltyAmount, applyAmount));
        data.setLawsuits(cases);
        data.setEnforcements(enforcements);
        data.setPenalties(penalties);
        data.setEvidenceCount(evidenceRefs.size());

        BigDecimal completeness = BigDecimal.valueOf(evidenceRefs.size())
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = findings.isEmpty()
                ? (gaps.isEmpty() ? new BigDecimal("0.90") : new BigDecimal("0.75"))
                : new BigDecimal("0.70");
        String summary = "司法风险分析完成：涉诉 " + cases.size() + " 件（原告 " + plaintiffCount
                + " / 被告 " + defendantCount + " / 第三人 " + thirdPartyCount + "），被告未结金额 "
                + money(pendingDefendantAmount) + " 元（占申请金额 " + ratioText(pendingDefendantAmountRatio)
                + "）；被执行人 " + executed.size() + " 条，行政处罚 " + penalties.size() + " 条；风险线索 "
                + findings.size() + " 条，数据缺口 " + gaps.size() + " 项";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, AgentContext context, String enterpriseName, String creditCode) {
        Map<String, Object> params = Map.of(
                "enterpriseName", nullToEmpty(enterpriseName),
                "creditCode", nullToEmpty(creditCode),
                "enterpriseId", context.getEnterpriseId() == null ? "" : String.valueOf(context.getEnterpriseId()));
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(new ToolRequest(context.getTaskId(), params));
    }

    private boolean isMock(ToolResult<?> result) {
        return result != null && Boolean.TRUE.equals(result.getMetadata().get("mock"));
    }

    private void addEvidenceRef(List<EvidenceRef> evidenceRefs, Long evidenceId, String sourceSystem,
                                String sourceReference, String dataPeriod, String contentSummary) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(String.valueOf(evidenceId));
        ref.setEvidenceType(EvidenceType.JUDICIAL_RECORD.name());
        ref.setSourceSystem(sourceSystem);
        ref.setSourceReference(sourceReference);
        ref.setDataPeriod(dataPeriod);
        ref.setExcerpt(contentSummary);
        evidenceRefs.add(ref);
    }

    private BigDecimal sumAmounts(List<BigDecimal> amounts) {
        return amounts.stream()
                .map(amount -> amount == null ? BigDecimal.ZERO : amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 金额占比（百分比，保留 2 位）：分母非正时返回 null，避免除零 */
    private BigDecimal ratio(BigDecimal amount, BigDecimal base) {
        if (amount == null || base == null || base.signum() <= 0) {
            return null;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String ratioText(BigDecimal ratio) {
        return ratio == null ? "无法计算" : ratio.toPlainString() + "%";
    }

    private AgentResult<JudicialRiskData> result(AgentContext context, JudicialRiskData data,
                                                 List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                 List<AgentSupplementRequest> supplementRequests,
                                                 List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                 BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<JudicialRiskData> result = new AgentResult<>();
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
                                 String status, BigDecimal confidence, List<EvidenceRef> evidenceRefs,
                                 String verificationSuggestion) {
        AgentFinding finding = new AgentFinding();
        finding.setFindingCode(code);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setDescription(description);
        finding.setStatus(status);
        finding.setConfidence(confidence);
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
        gap.setSourceType(EvidenceType.JUDICIAL_RECORD.name());
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
        definition.setAgentName("司法风险 Agent");
        definition.setDescription("分析企业涉诉、被执行、失信与行政处罚记录，输出司法风险线索与数据缺口");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("SUBJECT_VERIFICATION"));
        definition.setRequiredTools(List.of(TOOL_JUDICIAL_RECORDS, TOOL_ENFORCEMENT_RECORDS, TOOL_ADMIN_PENALTIES));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
