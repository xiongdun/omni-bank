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
import com.omnibank.service.agent.dd.tool.impl.CreditCodeVerifyResult;
import com.omnibank.service.agent.dd.tool.impl.EnterpriseRegistrySnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 01 主体信息核验：基于规则校验主体身份、统一社会信用代码与多来源一致性。
 * 当前不调用大模型，结论均来自确定性规则或已采集证据，Mock 模式下只用于链路验证。
 */
@Component
@RequiredArgsConstructor
public class SubjectVerificationAgent implements CreditAgent<SubjectVerificationInput, SubjectVerificationData> {

    public static final String AGENT_CODE = "SUBJECT_VERIFICATION";

    private static final String TOOL_QUERY_ENTERPRISE = "enterprise.queryEnterprise";

    private static final String TOOL_VERIFY_CREDIT_CODE = "enterprise.verifyCreditCode";

    /** 视为企业存续的经营状态 */
    private static final Set<String> ACTIVE_BUSINESS_STATUS = Set.of("存续", "在业", "正常", "开业", "登记成立");

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "主体信息核验 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<SubjectVerificationData> execute(AgentContext context, SubjectVerificationInput input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        SubjectVerificationData data = new SubjectVerificationData();

        if (context.isMockMode()) {
            warnings.add("当前证据来自 Mock Adapter（isMock=true），结论仅用于链路验证");
        }

        EnterpriseVO profile = context.getEnterpriseProfile();
        if (profile == null) {
            gaps.add(dataGap("ENTERPRISE_PROFILE", "企业主体信息", "上下文缺少企业主体信息", true,
                    "NOT_PROVIDED", "无法完成主体身份核验", "补充企业主体信息"));
            supplementRequests.add(supplement("ENTERPRISE_PROFILE", "企业主体信息", "主体核验缺少企业信息",
                    "补充企业名称与统一社会信用代码"));
            data.setEvidenceCount(0);
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), BigDecimal.ZERO, "主体信息核验未完成：缺少企业主体信息");
        }

        EvidenceRef registryRef = queryRegistry(context, evidenceRefs);
        if (registryRef == null) {
            gaps.add(dataGap("ENTERPRISE_REGISTRATION", "工商登记信息", "未获取到工商登记证据", true,
                    "UNAVAILABLE", "无法核验企业登记信息", "重新采集工商登记数据"));
            supplementRequests.add(supplement("ENTERPRISE_REGISTRATION", "工商登记信息",
                    "主体核验缺少工商登记证据", "重新采集或上传营业执照"));
        }
        data.setRegistryEvidenceFound(registryRef != null);

        String profileCode = profile.getCreditCode();
        String profileName = profile.getEnterpriseName();
        String declaredCode = input == null ? null : input.creditCode();
        String declaredName = input == null ? null : input.enterpriseName();

        CreditCodeVerifyResult creditCodeCheck = verifyCreditCode(context, profileCode);
        data.setCreditCodeValid(creditCodeCheck.valid());
        data.setCreditCodeInvalidReason(creditCodeCheck.reason());
        if (!creditCodeCheck.valid()) {
            findings.add(finding("SUBJECT_CREDIT_CODE_INVALID", "IDENTITY", "HIGH", "统一社会信用代码校验失败",
                    "统一社会信用代码【" + safe(profileCode) + "】未通过校验：" + creditCodeCheck.reason(),
                    "CONFIRMED", new BigDecimal("0.99"), evidenceRefs, "核对营业执照上的统一社会信用代码"));
        }

        boolean codeConsistent = declaredCode == null || declaredCode.trim().equalsIgnoreCase(nullToEmpty(profileCode));
        boolean nameConsistent = declaredName == null || declaredName.trim().equals(nullToEmpty(profileName));
        data.setCreditCodeConsistent(codeConsistent);
        data.setNameConsistent(nameConsistent);
        if (!codeConsistent) {
            findings.add(finding("SUBJECT_CREDIT_CODE_MISMATCH", "IDENTITY", "HIGH", "申报主体与登记主体代码不一致",
                    "申报代码【" + declaredCode + "】与登记代码【" + profileCode + "】不一致",
                    "NEEDS_VERIFICATION", new BigDecimal("0.85"), evidenceRefs,
                    "核实授信申请主体与营业执照主体是否一致"));
        }
        if (!nameConsistent) {
            findings.add(finding("SUBJECT_NAME_MISMATCH", "IDENTITY", "MEDIUM", "申报主体与登记主体名称不一致",
                    "申报名称【" + declaredName + "】与登记名称【" + profileName + "】不一致；名称相似不代表主体相同",
                    "NEEDS_VERIFICATION", new BigDecimal("0.80"), evidenceRefs,
                    "核实授信申请主体与营业执照主体是否一致"));
        }

        String businessStatus = profile.getBusinessStatus();
        if (isBlank(businessStatus)) {
            gaps.add(dataGap("ENTERPRISE_BUSINESS_STATUS", "企业经营状态", "企业主体信息缺少经营状态", true,
                    "NOT_PROVIDED", "无法判断企业是否持续经营", "补充工商登记经营状态"));
        } else if (!ACTIVE_BUSINESS_STATUS.contains(businessStatus.trim())) {
            findings.add(finding("SUBJECT_BUSINESS_STATUS_ABNORMAL", "OPERATION", "HIGH", "企业经营状态异常",
                    "企业当前经营状态为【" + businessStatus + "】，需核实是否影响授信",
                    "CONFIRMED", new BigDecimal("0.90"), evidenceRefs, "核实企业经营状态及存续情况"));
        }
        if (isBlank(profile.getLegalPerson())) {
            gaps.add(dataGap("LEGAL_PERSON", "法定代表人", "企业主体信息缺少法定代表人", true,
                    "NOT_PROVIDED", "无法核验实际控制人线索", "补充工商登记法定代表人"));
        }

        data.setEnterpriseName(profileName);
        data.setCreditCode(profileCode);
        data.setCustomerId(profile.getCustomerId());
        data.setLegalPerson(profile.getLegalPerson());
        data.setBusinessStatus(businessStatus);
        data.setEvidenceCount(evidenceRefs.size());
        data.setEvidenceCompleteness(completeness(profile));

        BigDecimal confidence = findings.isEmpty()
                ? (gaps.isEmpty() ? new BigDecimal("0.95") : new BigDecimal("0.80"))
                : new BigDecimal("0.70");
        String summary = "主体信息核验完成：统一社会信用代码" + (creditCodeCheck.valid() ? "校验通过" : "校验未通过")
                + "，申报主体" + (nameConsistent && codeConsistent ? "一致" : "存在不一致")
                + "；风险线索 " + findings.size() + " 条，数据缺口 " + gaps.size() + " 项";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, data.getEvidenceCompleteness(), summary);
    }

    private EvidenceRef queryRegistry(AgentContext context, List<EvidenceRef> evidenceRefs) {
        ToolResult<EnterpriseRegistrySnapshot> registry = call(TOOL_QUERY_ENTERPRISE,
                new ToolRequest(context.getTaskId(), Map.of()));
        if (!registry.isSuccess() || registry.getData() == null) {
            return null;
        }
        EnterpriseRegistrySnapshot snapshot = registry.getData();
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(String.valueOf(snapshot.evidenceId()));
        ref.setEvidenceType(EvidenceType.ENTERPRISE_PROFILE.name());
        ref.setSourceSystem(snapshot.sourceSystem());
        ref.setSourceReference(snapshot.sourceReference());
        ref.setDataPeriod(snapshot.dataPeriod());
        ref.setExcerpt(snapshot.contentSummary());
        evidenceRefs.add(ref);
        return ref;
    }

    private CreditCodeVerifyResult verifyCreditCode(AgentContext context, String creditCode) {
        ToolResult<CreditCodeVerifyResult> result = call(TOOL_VERIFY_CREDIT_CODE,
                new ToolRequest(context.getTaskId(), Map.of("creditCode", creditCode == null ? "" : creditCode)));
        if (!result.isSuccess() || result.getData() == null) {
            return new CreditCodeVerifyResult(false, result.getErrorMessage() == null ? "校验工具未返回结果" : result.getErrorMessage());
        }
        return result.getData();
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private BigDecimal completeness(EnterpriseVO profile) {
        String[] required = {profile.getEnterpriseName(), profile.getCreditCode(), profile.getLegalPerson(),
                profile.getBusinessStatus(), profile.getRegisteredAddress()};
        long present = java.util.Arrays.stream(required).filter(value -> !isBlank(value)).count();
        return BigDecimal.valueOf(present).divide(BigDecimal.valueOf(required.length), 2, RoundingMode.HALF_UP);
    }

    private AgentResult<SubjectVerificationData> result(AgentContext context, SubjectVerificationData data,
                                                        List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                        List<AgentSupplementRequest> supplementRequests,
                                                        List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                        BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<SubjectVerificationData> result = new AgentResult<>();
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
        result.setModelVersion("RULE_ENGINE");
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
        gap.setSourceType(EvidenceType.ENTERPRISE_PROFILE.name());
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
        definition.setAgentName("主体信息核验 Agent");
        definition.setDescription("校验企业主体身份、统一社会信用代码与多来源主体信息一致性");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of());
        definition.setRequiredTools(List.of(TOOL_QUERY_ENTERPRISE, TOOL_VERIFY_CREDIT_CODE));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
