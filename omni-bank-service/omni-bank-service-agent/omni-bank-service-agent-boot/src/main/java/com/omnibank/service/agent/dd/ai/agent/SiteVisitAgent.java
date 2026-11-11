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
import com.omnibank.service.agent.dd.tool.impl.InterviewSnapshot;
import com.omnibank.service.agent.dd.tool.impl.SiteInvestigationSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 05 现场调查：基于确定性规则比对现场观察事实（FACT）与访谈陈述（STATEMENT）。
 * 当前不调用大模型；访谈内容只能作为待核实线索，不得标记为已确认。
 */
@Component
@RequiredArgsConstructor
public class SiteVisitAgent implements CreditAgent<SiteVisitInput, SiteVisitData> {

    public static final String AGENT_CODE = "SITE_VISIT";

    private static final String TOOL_INVESTIGATION_RECORDS = "siteVisit.getInvestigationRecords";

    private static final String TOOL_INTERVIEW_RECORDS = "siteVisit.getInterviewRecords";

    /** 设备开机率低于该值视为开工不足 */
    private static final BigDecimal IDLE_EQUIPMENT_THRESHOLD = new BigDecimal("50");

    /** 访谈与现场观察偏差阈值（%） */
    private static final BigDecimal INTERVIEW_DEVIATION_THRESHOLD = new BigDecimal("20");

    private static final String NATURE_FACT = "FACT";

    private static final String NATURE_STATEMENT = "STATEMENT";

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "现场调查 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<SiteVisitData> execute(AgentContext context, SiteVisitInput input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        SiteVisitData data = new SiteVisitData();

        EnterpriseVO profile = context.getEnterpriseProfile();
        String enterpriseName = firstNonBlank(input == null ? null : input.enterpriseName(),
                profile == null ? null : profile.getEnterpriseName());
        String creditCode = firstNonBlank(input == null ? null : input.creditCode(),
                profile == null ? null : profile.getCreditCode());
        String registeredAddress = profile == null ? null : profile.getRegisteredAddress();

        ToolResult<SiteInvestigationSnapshot> investigationResult = call(TOOL_INVESTIGATION_RECORDS, context,
                enterpriseName, creditCode, registeredAddress);
        ToolResult<InterviewSnapshot> interviewResult = call(TOOL_INTERVIEW_RECORDS, context,
                enterpriseName, creditCode, registeredAddress);

        if (context.isMockMode() || isMock(investigationResult) || isMock(interviewResult)) {
            warnings.add("现场调查与访谈数据来自 Mock 数据源（来源系统以 MOCK_ 开头），结论仅用于链路验证");
        }

        SiteInvestigationSnapshot investigation = investigationResult.getData();
        InterviewSnapshot interview = interviewResult.getData();

        if (investigation != null) {
            addEvidenceRef(evidenceRefs, investigation.evidenceId(), investigation.sourceReference(),
                    investigation.sourceSystem(), investigation.dataPeriod(), investigation.contentSummary());
        } else {
            gaps.add(dataGap("SITE_VISIT_MISSING", "现场调查记录", "未获取到现场调查记录", true, "UNAVAILABLE",
                    "无法获得经营场所、设备与人员的现场观察事实", "安排客户经理补充现场调查"));
            supplementRequests.add(supplement("SITE_VISIT_MISSING", "现场调查记录",
                    "现场调查 Agent 缺少调查记录", "安排客户经理现场走访并上传调查记录与照片"));
        }
        if (interview != null) {
            addEvidenceRef(evidenceRefs, interview.evidenceId(), interview.sourceReference(),
                    interview.sourceSystem(), interview.dataPeriod(), interview.contentSummary());
        } else {
            gaps.add(dataGap("INTERVIEW_RECORD_MISSING", "管理层访谈记录", "未获取到管理层访谈记录", true,
                    "UNAVAILABLE", "无法比对访谈陈述与现场观察事实", "补充管理层访谈纪要"));
            supplementRequests.add(supplement("INTERVIEW_RECORD_MISSING", "管理层访谈记录",
                    "现场调查 Agent 缺少访谈记录", "补充管理层访谈纪要（产能、订单、人员与上下游情况）"));
        }

        if (registeredAddress == null || registeredAddress.isBlank()) {
            gaps.add(dataGap("REGISTERED_ADDRESS_MISSING", "工商注册地址", "企业主体信息缺少注册地址", true,
                    "NOT_PROVIDED", "无法比对现场地址与工商注册地址", "补充工商登记注册地址"));
        }

        String siteAddress = investigation == null ? null : investigation.siteAddress();
        Boolean addressConsistent = null;
        if (!isBlank(registeredAddress) && !isBlank(siteAddress)) {
            addressConsistent = normalizeAddress(registeredAddress).equals(normalizeAddress(siteAddress));
        }
        if (Boolean.FALSE.equals(addressConsistent)) {
            findings.add(finding("SITE_ADDRESS_MISMATCH", "SITE", "MEDIUM", "现场地址与工商注册地址不一致",
                    "现场调查地址【" + siteAddress + "】与工商注册地址【" + registeredAddress
                            + "】不一致，需核实实际经营场所与登记信息差异原因",
                    "NEEDS_VERIFICATION", new BigDecimal("0.70"), evidenceRefs,
                    "核实实际经营场所是否变更，必要时补充租赁合同或产权证明"));
        }

        BigDecimal equipmentOperatingRate = investigation == null
                ? null : investigation.equipmentOperatingRate();
        if (equipmentOperatingRate != null
                && equipmentOperatingRate.compareTo(IDLE_EQUIPMENT_THRESHOLD) < 0) {
            findings.add(finding("SITE_IDLE_EQUIPMENT", "SITE", "HIGH", "现场设备开工不足",
                    "现场观察设备开机率 " + equipmentOperatingRate.toPlainString()
                            + "%，低于 50%，属于现场观察事实（FACT）",
                    "CONFIRMED", new BigDecimal("0.90"), evidenceRefs,
                    "核实停产原因、产能恢复计划及对还款来源的影响"));
        }

        if (investigation != null && investigation.photoCount() == 0) {
            findings.add(finding("SITE_PHOTO_INSUFFICIENT", "SITE", "LOW", "现场调查照片不足",
                    "现场调查记录照片数量为 0，无法佐证现场观察事实（FACT）",
                    "CONFIRMED", new BigDecimal("0.80"), evidenceRefs,
                    "补充经营场所、设备与库存现场照片"));
        }

        BigDecimal capacityUtilizationRate = interview == null ? null : interview.capacityUtilizationRate();
        BigDecimal capacityDeviationRate = deviation(capacityUtilizationRate, equipmentOperatingRate);
        BigDecimal staffDeviationRate = deviation(
                interview == null ? null : BigDecimal.valueOf(interview.employeeCount()),
                investigation == null ? null : BigDecimal.valueOf(investigation.onSiteStaffCount()));
        boolean capacityInconsistent = capacityDeviationRate != null
                && capacityDeviationRate.compareTo(INTERVIEW_DEVIATION_THRESHOLD) > 0;
        boolean staffInconsistent = staffDeviationRate != null
                && staffDeviationRate.compareTo(INTERVIEW_DEVIATION_THRESHOLD) > 0;
        if (capacityInconsistent || staffInconsistent) {
            findings.add(finding("SITE_INTERVIEW_INCONSISTENT", "SITE", "MEDIUM", "访谈陈述与现场观察存在偏差",
                    "访谈声称产能利用率 " + text(capacityUtilizationRate) + "%（访谈陈述，STATEMENT），现场设备开机率 "
                            + text(equipmentOperatingRate) + "%（现场事实，FACT），偏差 "
                            + text(capacityDeviationRate) + "%；访谈员工人数 "
                            + (interview == null ? "未知" : interview.employeeCount()) + " 人，现场在岗 "
                            + (investigation == null ? "未知" : investigation.onSiteStaffCount()) + " 人，偏差 "
                            + text(staffDeviationRate) + "%，超过 20% 阈值",
                    "NEEDS_VERIFICATION", new BigDecimal("0.65"), evidenceRefs,
                    "核实产能利用与人员规模的真实口径，访谈陈述未经现场确认不得作为已核实结论"));
        }

        data.setEnterpriseName(enterpriseName);
        data.setCreditCode(creditCode);
        data.setRegisteredAddress(registeredAddress);
        data.setSiteAddress(siteAddress);
        data.setAddressConsistent(addressConsistent);
        if (investigation != null) {
            data.setInvestigationDate(investigation.investigationDate());
            data.setProductionStatus(investigation.productionStatus());
            data.setOnSiteStaffCount(investigation.onSiteStaffCount());
            data.setEquipmentOperatingRate(equipmentOperatingRate);
            data.setInventoryObservation(investigation.inventoryObservation());
            data.setPhotoCount(investigation.photoCount());
            data.setInvestigator(investigation.investigator());
            data.setObservationNature(NATURE_FACT);
            data.setObservationEvidenceId(String.valueOf(investigation.evidenceId()));
        }
        if (interview != null) {
            data.setInterviewDate(interview.interviewDate());
            data.setCapacityUtilizationRate(capacityUtilizationRate);
            data.setOrderBacklog(interview.orderBacklog());
            data.setInterviewEmployeeCount(interview.employeeCount());
            data.setMainCustomersStatement(interview.mainCustomersStatement());
            data.setMainSuppliersStatement(interview.mainSuppliersStatement());
            data.setInterviewee(interview.interviewee());
            data.setStatementNature(NATURE_STATEMENT);
            data.setStatementEvidenceId(String.valueOf(interview.evidenceId()));
        }
        data.setCapacityDeviationRate(capacityDeviationRate);
        data.setStaffDeviationRate(staffDeviationRate);
        data.setEvidenceCount(evidenceRefs.size());

        BigDecimal completeness = BigDecimal.valueOf(evidenceRefs.size())
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = findings.isEmpty()
                ? (gaps.isEmpty() ? new BigDecimal("0.90") : new BigDecimal("0.75"))
                : new BigDecimal("0.70");
        String summary = "现场调查分析完成：现场地址与注册地址"
                + (addressConsistent == null ? "无法比对" : addressConsistent ? "一致" : "不一致")
                + "；设备开机率 " + text(equipmentOperatingRate) + "%（现场事实，FACT），访谈声称产能利用率 "
                + text(capacityUtilizationRate) + "%（访谈陈述，STATEMENT）；风险线索 " + findings.size()
                + " 条，数据缺口 " + gaps.size() + " 项";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, AgentContext context, String enterpriseName,
                                   String creditCode, String registeredAddress) {
        Map<String, Object> params = Map.of(
                "enterpriseName", nullToEmpty(enterpriseName),
                "creditCode", nullToEmpty(creditCode),
                "registeredAddress", nullToEmpty(registeredAddress),
                "enterpriseId", context.getEnterpriseId() == null ? "" : String.valueOf(context.getEnterpriseId()));
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(new ToolRequest(context.getTaskId(), params));
    }

    private boolean isMock(ToolResult<?> result) {
        return result != null && Boolean.TRUE.equals(result.getMetadata().get("mock"));
    }

    private void addEvidenceRef(List<EvidenceRef> evidenceRefs, Long evidenceId, String sourceReference,
                                String sourceSystem, String dataPeriod, String contentSummary) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(String.valueOf(evidenceId));
        ref.setEvidenceType(EvidenceType.SITE_INVESTIGATION.name());
        ref.setSourceSystem(sourceSystem);
        ref.setSourceReference(sourceReference);
        ref.setDataPeriod(dataPeriod);
        ref.setExcerpt(contentSummary);
        evidenceRefs.add(ref);
    }

    /** 偏差率（百分比，保留 2 位）：观察值为空或非正时返回 null，避免除零 */
    private BigDecimal deviation(BigDecimal claimed, BigDecimal observed) {
        if (claimed == null || observed == null || observed.signum() <= 0) {
            return null;
        }
        return claimed.subtract(observed).abs().multiply(BigDecimal.valueOf(100))
                .divide(observed, 2, RoundingMode.HALF_UP);
    }

    private String text(BigDecimal value) {
        return value == null ? "未知" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalizeAddress(String address) {
        return address == null ? "" : address.replaceAll("\\s+", "");
    }

    private AgentResult<SiteVisitData> result(AgentContext context, SiteVisitData data,
                                              List<AgentFinding> findings, List<AgentDataGap> gaps,
                                              List<AgentSupplementRequest> supplementRequests,
                                              List<EvidenceRef> evidenceRefs, List<String> warnings,
                                              BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<SiteVisitData> result = new AgentResult<>();
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
        gap.setSourceType(EvidenceType.SITE_INVESTIGATION.name());
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
        definition.setAgentName("现场调查 Agent");
        definition.setDescription("分析现场调查记录与管理层访谈，区分现场观察事实与访谈陈述并识别差异");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("DOCUMENT_RECOGNITION", "SUBJECT_VERIFICATION"));
        definition.setRequiredTools(List.of(TOOL_INVESTIGATION_RECORDS, TOOL_INTERVIEW_RECORDS));
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
