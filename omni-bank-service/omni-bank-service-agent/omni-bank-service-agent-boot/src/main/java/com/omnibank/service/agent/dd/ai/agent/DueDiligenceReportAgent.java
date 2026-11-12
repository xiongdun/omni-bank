package com.omnibank.service.agent.dd.ai.agent;

import com.omnibank.service.agent.dd.ai.AgentContext;
import com.omnibank.service.agent.dd.ai.AgentDataGap;
import com.omnibank.service.agent.dd.ai.AgentDefinition;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentSupplementRequest;
import com.omnibank.service.agent.dd.ai.CreditAgent;
import com.omnibank.service.agent.dd.ai.EvidenceRef;
import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.dto.enterprise.EnterpriseVO;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.enums.ReportReviewStatus;
import com.omnibank.service.agent.dd.enums.ReportStatus;
import com.omnibank.service.agent.dd.plan.DdAgentCatalog;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.impl.ReportSnapshots;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 尽调报告 Agent：按固定 18 章模板汇总上游结果，生成结构化草稿并落库版本；
 * 仅输出导出元信息，不渲染 Word/PDF，报告须客户经理复核，不代表授信审批通过。
 */
@Component
@RequiredArgsConstructor
public class DueDiligenceReportAgent implements CreditAgent<Void, DueDiligenceReportData> {

    public static final String AGENT_CODE = DdAgentCatalog.DUE_DILIGENCE_REPORT;

    public static final String GENERATION_NOTE =
            "本报告由 AI 基于上游分析结果自动生成草稿，须客户经理复核确认，不代表授信审批通过；"
                    + "AI 不输出最终审批金额、不输出合规结论";

    public static final String EXPORT_NOTE =
            "P7 仅生成结构化报告数据，Word/PDF 渲染能力未接入，导出能力在后续阶段实现";

    private static final String TOOL_TEMPLATE = "report.getTemplate";

    private static final String TOOL_VALIDATE = "report.validateReport";

    private static final String TOOL_VERSION = "report.createReportVersion";

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    private static final String STATUS_MISSING_INPUT = "MISSING_INPUT";

    private static final String STATUS_AGGREGATED = "AGGREGATED";

    private static final String STATUS_MANUAL_REVIEW = "MANUAL_REVIEW_PENDING";

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
            DdAgentCatalog.POLICY_MATCHING,
            DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT);

    private final ToolRegistry toolRegistry;

    private final DdJsonSupport jsonSupport;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "尽调报告 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return buildDefinition();
    }

    @Override
    public AgentResult<DueDiligenceReportData> execute(AgentContext context, Void input) {
        List<String> warnings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplements = new ArrayList<>();
        Map<String, UpstreamAgentResults.Snapshot> snapshots = UpstreamAgentResults.collect(
                context.getUpstreamResults(), UPSTREAM_AGENTS, jsonSupport);

        ReportSnapshots.TemplateResult template = loadTemplate(context, warnings);
        List<DueDiligenceReportData.ReportChapter> chapters = new ArrayList<>();
        List<Map<String, Object>> chapterMaps = new ArrayList<>();
        for (ReportSnapshots.ChapterTemplate chapterTemplate : template.chapters()) {
            ChapterDraft draft = buildChapter(chapterTemplate, snapshots, context);
            DueDiligenceReportData.ReportChapter chapter = new DueDiligenceReportData.ReportChapter();
            chapter.setChapterNo(chapterTemplate.chapterNo());
            chapter.setChapterCode(chapterTemplate.chapterCode());
            chapter.setTitle(chapterTemplate.title());
            chapter.setContent(draft.content);
            chapter.setStatus(draft.status);
            chapter.setEvidenceIds(List.copyOf(draft.evidenceIds));
            chapter.setDataGaps(List.copyOf(draft.dataGaps));
            chapter.setLimitation(draft.limitation);
            chapters.add(chapter);

            Map<String, Object> chapterMap = new LinkedHashMap<>();
            chapterMap.put("chapterCode", chapter.getChapterCode());
            chapterMap.put("status", chapter.getStatus());
            chapterMap.put("content", chapter.getContent());
            chapterMap.put("evidenceIds", chapter.getEvidenceIds());
            chapterMaps.add(chapterMap);

            if (chapterTemplate.required() && !STATUS_AVAILABLE.equals(chapter.getStatus())) {
                gaps.add(dataGap("REPORT_CHAPTER_" + chapterTemplate.chapterCode(), chapterTemplate.title(),
                        "必备章节未能由上游结果生成", true, "UNAVAILABLE",
                        "报告缺少必备章节，不能作为完整尽调报告使用",
                        "补充对应资料并重新执行上游 Agent"));
                supplements.add(supplement("REPORT_CHAPTER_" + chapterTemplate.chapterCode(),
                        chapterTemplate.title(), "必备章节缺少上游结果",
                        "补齐资料后重新生成报告"));
            }
        }

        ReportSnapshots.ValidationResult validation = validate(context, chapterMaps, warnings);

        Set<String> evidenceIds = new LinkedHashSet<>();
        chapters.forEach(chapter -> evidenceIds.addAll(chapter.getEvidenceIds()));
        int upstreamGapCount = snapshots.values().stream().mapToInt(snapshot -> snapshot.dataGaps().size()).sum();
        int upstreamSupplementCount = snapshots.values().stream()
                .mapToInt(snapshot -> snapshot.supplementRequests().size()).sum();

        DueDiligenceReportData data = new DueDiligenceReportData();
        data.setTaskId(context.getTaskId());
        data.setReportStatus(ReportStatus.AI_GENERATED.name());
        data.setReviewStatus(ReportReviewStatus.NOT_SUBMITTED.name());
        data.setDraftComplete(validation.valid());
        data.setTitle(reportTitle(context));
        data.setTemplateVersion(template.templateVersion());
        data.setChapters(chapters);
        data.setEvidenceCount(evidenceIds.size());
        data.setDataGapCount(upstreamGapCount + gaps.size());
        data.setSupplementRequestCount(upstreamSupplementCount + supplements.size());
        data.setMissingInputs(missingInputs(template, chapters));
        data.setValidationIssues(validation.issues());
        data.setCompleteness(completeness(template, chapters));
        data.setConfidence(confidence(data.getCompleteness()));
        DueDiligenceReportData.ExportInfo exportInfo = new DueDiligenceReportData.ExportInfo();
        exportInfo.setWordStatus("NOT_AVAILABLE");
        exportInfo.setPdfStatus("NOT_AVAILABLE");
        exportInfo.setNote(EXPORT_NOTE);
        data.setExportInfo(exportInfo);
        data.setGenerationNote(GENERATION_NOTE);

        persistVersion(context, data, evidenceIds, warnings, gaps);

        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        Map<String, EvidenceRef> evidenceIndex = indexEvidence(context);
        for (String evidenceId : evidenceIds) {
            evidenceRefs.add(evidenceIndex.getOrDefault(evidenceId, minimalRef(evidenceId)));
        }

        warnings.add(GENERATION_NOTE);
        if (context.isMockMode()) {
            warnings.add("上游分析基于 Mock 证据（isMock=true），报告内容仅用于链路验证");
        }

        AgentResult<DueDiligenceReportData> result = new AgentResult<>();
        result.setAgentCode(AGENT_CODE);
        result.setExecutionId(context.getExecutionId());
        result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
        result.setSummary(summary(data));
        result.setData(data);
        result.setFindings(List.of());
        result.setDataGaps(gaps);
        result.setSupplementRequests(supplements);
        result.setEvidenceRefs(evidenceRefs);
        result.setConfidence(data.getConfidence());
        result.setEvidenceCompleteness(data.getCompleteness());
        result.setWarnings(warnings);
        result.setModelVersion("rule-engine");
        result.setPromptVersion("rule-1.0");
        return result;
    }

    private ReportSnapshots.TemplateResult loadTemplate(AgentContext context, List<String> warnings) {
        ToolResult<ReportSnapshots.TemplateResult> toolResult = call(TOOL_TEMPLATE,
                new ToolRequest(context.getTaskId(), Map.of()));
        if (toolResult.isSuccess() && toolResult.getData() != null) {
            return toolResult.getData();
        }
        warnings.add("报告模板读取失败，已按空模板继续，报告状态标记为不完整");
        return new ReportSnapshots.TemplateResult("UNKNOWN", List.of());
    }

    private ReportSnapshots.ValidationResult validate(AgentContext context, List<Map<String, Object>> chapterMaps,
                                                      List<String> warnings) {
        ToolResult<ReportSnapshots.ValidationResult> toolResult = call(TOOL_VALIDATE,
                new ToolRequest(context.getTaskId(), Map.of("chapters", chapterMaps)));
        if (toolResult.isSuccess() && toolResult.getData() != null) {
            return toolResult.getData();
        }
        warnings.add("报告完整性校验未执行，报告状态标记为不完整");
        return new ReportSnapshots.ValidationResult(false, List.of("报告完整性校验未执行"), List.of(), List.of());
    }

    private void persistVersion(AgentContext context, DueDiligenceReportData data, Set<String> evidenceIds,
                                List<String> warnings, List<AgentDataGap> gaps) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", data.getTitle());
        params.put("reportStatus", data.getReportStatus());
        params.put("reviewStatus", data.getReviewStatus());
        params.put("chapters", data.getChapters());
        params.put("evidenceIds", List.copyOf(evidenceIds));
        params.put("evidenceCount", data.getEvidenceCount());
        params.put("dataGapCount", data.getDataGapCount());
        params.put("supplementRequestCount", data.getSupplementRequestCount());
        params.put("exportStatus", "NOT_AVAILABLE");
        params.put("modelVersion", "rule-engine");
        params.put("promptVersion", "rule-1.0");
        ToolResult<ReportSnapshots.VersionResult> toolResult = call(TOOL_VERSION,
                new ToolRequest(context.getTaskId(), params));
        if (toolResult.isSuccess() && toolResult.getData() != null) {
            ReportSnapshots.VersionResult version = toolResult.getData();
            data.setReportId(version.reportId());
            data.setReportNo(version.reportNo());
            data.setReportVersion(version.reportVersion());
            return;
        }
        warnings.add("报告版本落库失败，本次报告仅保留在 Agent 执行结果中");
        gaps.add(dataGap("REPORT_VERSION", "报告版本落库", "报告版本持久化失败", true, "UNAVAILABLE",
                "无法追溯报告版本", "检查报告表与数据库连接后重新生成"));
    }

    private ChapterDraft buildChapter(ReportSnapshots.ChapterTemplate template,
                                      Map<String, UpstreamAgentResults.Snapshot> snapshots,
                                      AgentContext context) {
        return switch (template.chapterCode()) {
            case "CREDIT_APPLICATION" -> applicationChapter(context);
            case "ENTERPRISE_PROFILE" -> enterpriseChapter(template, snapshots, context);
            case "EQUITY_RELATIONS" -> equityChapter(template, snapshots);
            case "BUSINESS_OPERATION" -> summaryChapter(template, snapshots, "企业经营情况");
            case "INDUSTRY_ANALYSIS" -> summaryChapter(template, snapshots, "行业分析");
            case "FINANCIAL_ANALYSIS" -> summaryChapter(template, snapshots, "财务分析及偿债能力");
            case "CREDIT_DEBT" -> summaryChapter(template, snapshots, "征信及债务情况");
            case "JUDICIAL_RISK" -> summaryChapter(template, snapshots, "司法风险分析");
            case "SITE_VISIT" -> summaryChapter(template, snapshots, "现场调查情况");
            case "MATERIAL_COMPLETENESS" -> summaryChapter(template, snapshots, "尽调材料完整性");
            case "LOAN_PURPOSE" -> loanPurposeChapter(template, snapshots);
            case "FRAUD_ANALYSIS" -> fraudChapter(template, snapshots);
            case "POLICY_MATCHING" -> policyChapter(template, snapshots);
            case "COMPREHENSIVE_RISK" -> comprehensiveChapter(template, snapshots);
            case "AMOUNT_TERM" -> amountTermChapter(template, snapshots);
            case "RISK_MITIGATION" -> mitigationChapter(template, snapshots);
            case "SUPPLEMENT_ITEMS" -> supplementChapter(snapshots);
            default -> reviewChapter(template);
        };
    }

    private ChapterDraft applicationChapter(AgentContext context) {
        LoanApplicationVO application = context.getCreditApplication();
        if (application == null) {
            return missing("授信申请信息缺失");
        }
        ChapterDraft draft = available("申请编号 " + dash(application.getApplicationNo())
                + "，产品 " + dash(application.getProductName()) + "（" + dash(application.getProductCode())
                + "），申请金额 " + dash(application.getApplyAmount()) + " " + dash(application.getCurrency())
                + "，期限 " + dash(application.getApplyTerm()) + " " + dash(application.getTermUnit())
                + "，贷款用途 " + dash(application.getLoanPurpose())
                + "，还款方式 " + dash(application.getRepaymentMethod())
                + "，担保方式 " + dash(application.getGuaranteeType()), List.of());
        draft.limitation = "申请信息取自进件快照，最终以有权审批人核定为准";
        return draft;
    }

    private ChapterDraft enterpriseChapter(ReportSnapshots.ChapterTemplate template,
                                           Map<String, UpstreamAgentResults.Snapshot> snapshots,
                                           AgentContext context) {
        UpstreamAgentResults.Snapshot subject = firstSucceeded(template, snapshots);
        if (subject == null) {
            return missing(missingMessage(template));
        }
        EnterpriseVO enterprise = context.getEnterpriseProfile();
        String profile = enterprise == null ? "企业主体信息缺失"
                : "企业名称 " + dash(enterprise.getEnterpriseName())
                        + "，统一社会信用代码 " + dash(enterprise.getCreditCode())
                        + "，法定代表人 " + dash(enterprise.getLegalPerson())
                        + "，注册地址 " + dash(enterprise.getRegisteredAddress())
                        + "，经营状态 " + dash(enterprise.getBusinessStatus())
                        + "，行业 " + dash(enterprise.getIndustryName());
        ChapterDraft draft = available(profile + "。" + dash(subject.summary()), sources(template, snapshots));
        draft.limitation = "主体一致性以主体核验结论为准，工商变更需复核";
        return draft;
    }

    private ChapterDraft equityChapter(ReportSnapshots.ChapterTemplate template,
                                       Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot fraud = firstSucceeded(template, snapshots);
        if (fraud == null) {
            return missing(missingMessage(template));
        }
        ChapterDraft draft = available("反欺诈分析结论：" + dash(fraud.summary())
                + "。股权结构明细数据未在证据中心提供，关联关系需客户经理结合工商档案核查",
                sources(template, snapshots));
        draft.limitation = "未获取股权结构明细，关联关系仅引用反欺诈线索";
        return draft;
    }

    private ChapterDraft summaryChapter(ReportSnapshots.ChapterTemplate template,
                                        Map<String, UpstreamAgentResults.Snapshot> snapshots, String heading) {
        List<UpstreamAgentResults.Snapshot> succeeded = sources(template, snapshots);
        if (succeeded.isEmpty()) {
            return missing(missingMessage(template));
        }
        List<String> summaries = new ArrayList<>();
        for (UpstreamAgentResults.Snapshot snapshot : succeeded) {
            if (snapshot.summary() != null && !snapshot.summary().isBlank()) {
                summaries.add(snapshot.summary());
            }
        }
        ChapterDraft draft = available(heading + "：" + (summaries.isEmpty() ? "上游未返回摘要" : String.join("；",
                summaries)), succeeded);
        draft.limitation = "内容来自上游 Agent 摘要，明细与待核实事项见对应分节";
        return draft;
    }

    private ChapterDraft loanPurposeChapter(ReportSnapshots.ChapterTemplate template,
                                            Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot loanPurpose = firstSucceeded(template, snapshots);
        if (loanPurpose == null) {
            return missing(missingMessage(template));
        }
        Map<String, Object> data = loanPurpose.data();
        String content = "贷款用途分类 " + dash(UpstreamAgentResults.dataText(data, "purposeCategory"))
                + "；测算需求 " + dash(UpstreamAgentResults.dataText(data, "measuredDemand"))
                + " 元；资金缺口 " + dash(UpstreamAgentResults.dataText(data, "fundingGap"))
                + " 元；金额合理性 " + dash(UpstreamAgentResults.dataText(data, "amountReasonableness"))
                + "；期限合理性 " + dash(UpstreamAgentResults.dataText(data, "termReasonableness"))
                + "。测算公式：" + dash(UpstreamAgentResults.dataText(data, "demandFormula"))
                + "（来源 " + dash(UpstreamAgentResults.dataText(data, "demandFormulaSource")) + "）";
        ChapterDraft draft = available(content, sources(template, snapshots));
        draft.limitation = "需求测算基于 Mock 口径，AI 不输出最终审批金额";
        return draft;
    }

    private ChapterDraft fraudChapter(ReportSnapshots.ChapterTemplate template,
                                      Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot fraud = firstSucceeded(template, snapshots);
        if (fraud == null) {
            return missing(missingMessage(template));
        }
        Map<String, Object> data = fraud.data();
        String content = dash(fraud.summary()) + "；命中规则 " + dash(UpstreamAgentResults.dataText(data,
                "ruleHitCount")) + " 条；风险影响：" + dash(UpstreamAgentResults.dataText(data, "riskImpact"))
                + "。所有规则命中均标记为待人工核实，不构成欺诈认定";
        ChapterDraft draft = available(content, sources(template, snapshots));
        draft.limitation = "反欺诈线索需人工核实，AI 不作欺诈认定";
        return draft;
    }

    private ChapterDraft policyChapter(ReportSnapshots.ChapterTemplate template,
                                       Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot policy = firstSucceeded(template, snapshots);
        if (policy == null) {
            return missing(missingMessage(template));
        }
        Map<String, Object> data = policy.data();
        ChapterDraft draft = available(dash(policy.summary()) + "；适用性说明："
                + dash(UpstreamAgentResults.dataText(data, "applicabilityNote"))
                + "；合规结论说明：" + dash(UpstreamAgentResults.dataText(data, "complianceConclusionNote")),
                sources(template, snapshots));
        draft.limitation = "政策匹配仅提供条款参考，AI 不输出合规结论";
        return draft;
    }

    private ChapterDraft comprehensiveChapter(ReportSnapshots.ChapterTemplate template,
                                              Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot risk = firstSucceeded(template, snapshots);
        if (risk == null) {
            return missing(missingMessage(template));
        }
        Map<String, Object> data = risk.data();
        ChapterDraft draft = available(dash(risk.summary()) + "；风险等级："
                + dash(UpstreamAgentResults.dataText(data, "riskLevel")) + "（"
                + dash(UpstreamAgentResults.dataText(data, "riskLevelNote")) + "）；具备完整评估条件："
                + (UpstreamAgentResults.dataBoolean(data, "assessable") ? "是" : "否")
                + "；数据完整度 " + dash(UpstreamAgentResults.dataText(data, "dataCompleteness")),
                sources(template, snapshots));
        draft.limitation = "综合评估不输出风险等级与最终审批金额，仅供审批参考";
        return draft;
    }

    private ChapterDraft amountTermChapter(ReportSnapshots.ChapterTemplate template,
                                           Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot risk = snapshots.get(DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT);
        if (!risk.succeeded()) {
            return missing(missingMessage(template));
        }
        List<String> concerns = stringList(risk.data().get("amountAndTermConcerns"));
        ChapterDraft draft = available("金额与期限关注事项：" + (concerns.isEmpty() ? "综合评估未输出关注事项"
                : String.join("；", concerns)), List.of(risk));
        draft.limitation = "AI 不输出最终审批金额与期限，最终额度由有权审批人确定";
        return draft;
    }

    private ChapterDraft mitigationChapter(ReportSnapshots.ChapterTemplate template,
                                           Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        UpstreamAgentResults.Snapshot risk = snapshots.get(DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT);
        if (!risk.succeeded()) {
            return missing(missingMessage(template));
        }
        List<String> suggestions = stringList(risk.data().get("mitigationSuggestions"));
        ChapterDraft draft = available("风险缓释建议：" + (suggestions.isEmpty() ? "综合评估未输出缓释建议"
                : String.join("；", suggestions)), List.of(risk));
        draft.limitation = "缓释建议为参考方案，需客户经理与审批人确认后执行";
        return draft;
    }

    private ChapterDraft supplementChapter(Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        List<String> items = new ArrayList<>();
        for (UpstreamAgentResults.Snapshot snapshot : snapshots.values()) {
            if (!snapshot.succeeded()) {
                continue;
            }
            for (Map<String, Object> gap : snapshot.dataGaps()) {
                items.add(dash(UpstreamAgentResults.dataText(gap, "dataName")) + "："
                        + dash(UpstreamAgentResults.dataText(gap, "description")));
            }
            for (Map<String, Object> request : snapshot.supplementRequests()) {
                items.add(dash(UpstreamAgentResults.dataText(request, "dataName")) + "："
                        + dash(UpstreamAgentResults.dataText(request, "reason")));
            }
        }
        ChapterDraft draft = new ChapterDraft();
        draft.status = STATUS_AGGREGATED;
        draft.content = items.isEmpty() ? "当前无待补充材料与调查事项，以客户经理复核结论为准"
                : "待补充材料与调查事项：" + String.join("；", items);
        draft.evidenceIds = List.of();
        draft.dataGaps = List.of();
        draft.limitation = "缺口与补充请求来自上游 Agent，需逐项闭环";
        return draft;
    }

    private ChapterDraft reviewChapter(ReportSnapshots.ChapterTemplate template) {
        ChapterDraft draft = new ChapterDraft();
        draft.status = STATUS_MANUAL_REVIEW;
        draft.content = "本章由客户经理填写复核意见：核实报告内容、补充调查事项与风险缓释措施后签署意见";
        draft.evidenceIds = List.of();
        draft.dataGaps = List.of();
        draft.limitation = template.title() + "不参与 AI 自动评分";
        return draft;
    }

    private ChapterDraft available(String content, List<UpstreamAgentResults.Snapshot> sources) {
        ChapterDraft draft = new ChapterDraft();
        draft.status = STATUS_AVAILABLE;
        draft.content = content;
        Set<String> evidenceIds = new LinkedHashSet<>();
        List<String> gaps = new ArrayList<>();
        for (UpstreamAgentResults.Snapshot source : sources) {
            evidenceIds.addAll(source.evidenceIds());
            for (Map<String, Object> gap : source.dataGaps()) {
                gaps.add(dash(UpstreamAgentResults.dataText(gap, "dataName")) + "："
                        + dash(UpstreamAgentResults.dataText(gap, "description")));
            }
        }
        draft.evidenceIds = List.copyOf(evidenceIds);
        draft.dataGaps = List.copyOf(gaps);
        return draft;
    }

    private ChapterDraft missing(String message) {
        ChapterDraft draft = new ChapterDraft();
        draft.status = STATUS_MISSING_INPUT;
        draft.content = message;
        draft.evidenceIds = List.of();
        draft.dataGaps = List.of(message);
        draft.limitation = "上游结果缺失，需补充资料后重新生成";
        return draft;
    }

    private UpstreamAgentResults.Snapshot firstSucceeded(ReportSnapshots.ChapterTemplate template,
                                                         Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        return sources(template, snapshots).stream().findFirst().orElse(null);
    }

    private List<UpstreamAgentResults.Snapshot> sources(ReportSnapshots.ChapterTemplate template,
                                                        Map<String, UpstreamAgentResults.Snapshot> snapshots) {
        List<UpstreamAgentResults.Snapshot> sources = new ArrayList<>();
        for (String agentCode : template.sourceAgents()) {
            UpstreamAgentResults.Snapshot snapshot = snapshots.get(agentCode);
            if (snapshot != null && snapshot.succeeded()) {
                sources.add(snapshot);
            }
        }
        return sources;
    }

    private String missingMessage(ReportSnapshots.ChapterTemplate template) {
        return "未获取该章节所需的上游结果（来源：" + String.join("、", template.sourceAgents())
                + "），需补充后重新分析";
    }

    private List<String> missingInputs(ReportSnapshots.TemplateResult template,
                                       List<DueDiligenceReportData.ReportChapter> chapters) {
        List<String> missing = new ArrayList<>();
        for (ReportSnapshots.ChapterTemplate chapterTemplate : template.chapters()) {
            if (!chapterTemplate.required()) {
                continue;
            }
            chapters.stream()
                    .filter(chapter -> chapter.getChapterCode().equals(chapterTemplate.chapterCode()))
                    .findFirst()
                    .filter(chapter -> !STATUS_AVAILABLE.equals(chapter.getStatus()))
                    .ifPresent(chapter -> missing.add(chapterTemplate.title()));
        }
        return List.copyOf(missing);
    }

    private BigDecimal completeness(ReportSnapshots.TemplateResult template,
                                    List<DueDiligenceReportData.ReportChapter> chapters) {
        long required = template.chapters().stream().filter(ReportSnapshots.ChapterTemplate::required).count();
        if (required == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long available = chapters.stream()
                .filter(chapter -> STATUS_AVAILABLE.equals(chapter.getStatus()))
                .filter(chapter -> template.chapters().stream().anyMatch(item -> item.chapterCode()
                        .equals(chapter.getChapterCode()) && item.required()))
                .count();
        return BigDecimal.valueOf(available).divide(BigDecimal.valueOf(required), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal confidence(BigDecimal completeness) {
        double value = Math.min(0.90, 0.55 + 0.35 * completeness.doubleValue());
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String reportTitle(AgentContext context) {
        EnterpriseVO enterprise = context.getEnterpriseProfile();
        String name = enterprise == null || enterprise.getEnterpriseName() == null
                ? "企业" : enterprise.getEnterpriseName();
        return name + "对公信贷尽调报告";
    }

    private String summary(DueDiligenceReportData data) {
        return "尽调报告草稿已生成：共 " + data.getChapters().size() + " 章，必备章节完成度 "
                + data.getCompleteness().toPlainString() + "，引用证据 " + data.getEvidenceCount()
                + " 条，数据缺口 " + data.getDataGapCount() + " 项，待补充材料 " + data.getSupplementRequestCount()
                + " 项，版本 V" + dash(data.getReportVersion()) + "；报告须客户经理复核，不代表审批通过";
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return List.copyOf(values);
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

    private String dash(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private static AgentDefinition buildDefinition() {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode(AGENT_CODE);
        definition.setAgentName("尽调报告 Agent");
        definition.setDescription("按固定 18 章模板汇总上游结果生成结构化报告草稿，落库报告版本；仅输出导出元信息，须人工复核");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(UPSTREAM_AGENTS);
        definition.setRequiredTools(List.of(TOOL_TEMPLATE, TOOL_VALIDATE, TOOL_VERSION));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    /** 章节草稿 */
    private static final class ChapterDraft {

        private String content;

        private String status;

        private List<String> evidenceIds = List.of();

        private List<String> dataGaps = List.of();

        private String limitation;
    }
}
