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
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.impl.IndustryBenchmarkSnapshot;
import com.omnibank.service.agent.dd.tool.impl.IndustryProfileSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 07 行业专家：识别企业所属行业并评估行业环境、政策、周期与集中度，再将企业指标与行业基准对比。
 * 行业数据来自 Mock 行业研究库；上游财务数据缺失时跳过对比并记数据缺口，不阻断执行。
 */
@Component
@RequiredArgsConstructor
public class IndustryExpertAgent implements CreditAgent<Void, IndustryExpertData> {

    public static final String AGENT_CODE = "INDUSTRY_EXPERT";

    private static final String TOOL_INDUSTRY_PROFILE = "industry.getIndustryProfile";

    private static final String TOOL_INDUSTRY_BENCHMARK = "industry.getIndustryBenchmark";

    private static final String UPSTREAM_FINANCIAL_ANALYSIS = "FINANCIAL_ANALYSIS";

    private static final String UNKNOWN_INDUSTRY = "UNKNOWN";

    /** 毛利率低于行业中位数的容忍阈值（百分点） */
    private static final BigDecimal MARGIN_GAP_THRESHOLD = new BigDecimal("-5");

    /** 资产负债率高于行业中位数的容忍阈值（百分点） */
    private static final BigDecimal LEVERAGE_GAP_THRESHOLD = new BigDecimal("15");

    /** CR5 高集中度阈值（%） */
    private static final BigDecimal HIGH_CONCENTRATION_THRESHOLD = new BigDecimal("70");

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "行业专家 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<IndustryExpertData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        IndustryExpertData data = new IndustryExpertData();
        warnings.add("当前行业画像与基准来自 Mock 行业研究提供方（MOCK_INDUSTRY_PROVIDER / "
                + "MOCK_INDUSTRY_BENCHMARK_PROVIDER），仅用于链路验证，不作为真实行业结论");

        Map<String, Object> params = industryParams(context);
        ToolResult<IndustryProfileSnapshot> profileResult = call(TOOL_INDUSTRY_PROFILE,
                new ToolRequest(context.getTaskId(), params));
        IndustryProfileSnapshot profile = profileResult.isSuccess() ? profileResult.getData() : null;
        if (profile == null) {
            gaps.add(dataGap("INDUSTRY_PROFILE", "行业画像", "行业画像工具未返回结果", true, "UNAVAILABLE",
                    "无法分析行业环境与竞争格局", "重新获取行业研究数据"));
            supplementRequests.add(supplement("INDUSTRY_PROFILE", "行业画像", "行业分析缺少行业数据",
                    "补充企业行业归属或行业研究报告"));
            data.setIndustryCode(UNKNOWN_INDUSTRY);
            data.setIndustryName("未知行业");
            data.setGrowthRates(List.of());
            data.setMainRisks(List.of());
            data.setEvidenceCount(0);
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "行业分析未完成：行业画像不可用");
        }

        boolean resolved = !UNKNOWN_INDUSTRY.equals(profile.industryCode());
        data.setIndustryCode(profile.industryCode());
        data.setIndustryName(profile.industryName());
        data.setMarketSize(profile.marketSize());
        data.setGrowthRates(profile.growthRates());
        data.setPolicyOrientation(profile.policyOrientation());
        data.setConcentrationCr5(profile.concentrationCr5());
        data.setCycleStage(profile.cycleStage());
        data.setMainRisks(profile.mainRisks());
        data.setDataPeriod(profile.dataPeriod());

        IndustryBenchmarkSnapshot benchmark = null;
        if (resolved) {
            addToolEvidenceRef(evidenceRefs, profileResult, "INDUSTRY_PROFILE:" + profile.industryCode(),
                    profile.dataPeriod(), "Mock 行业画像：" + profile.industryName() + "（"
                            + profile.industryCode() + "）");
            ToolResult<IndustryBenchmarkSnapshot> benchmarkResult = call(TOOL_INDUSTRY_BENCHMARK,
                    new ToolRequest(context.getTaskId(), params));
            benchmark = benchmarkResult.isSuccess() ? benchmarkResult.getData() : null;
            if (benchmark == null) {
                gaps.add(dataGap("INDUSTRY_BENCHMARK", "行业基准", "行业基准工具未返回结果", true, "UNAVAILABLE",
                        "无法对比企业与行业平均水平", "重新获取行业基准数据"));
            } else {
                data.setGrossMarginMedian(benchmark.grossMarginMedian());
                data.setAssetLiabilityRatioMedian(benchmark.assetLiabilityRatioMedian());
                data.setReceivableTurnoverDaysMedian(benchmark.receivableTurnoverDaysMedian());
                data.setRevenueGrowthMedian(benchmark.revenueGrowthMedian());
                addToolEvidenceRef(evidenceRefs, benchmarkResult, "INDUSTRY_BENCHMARK:" + benchmark.industryCode(),
                        benchmark.dataPeriod(), "Mock 行业基准：" + benchmark.industryName()
                                + "（" + benchmark.industryCode() + "）");
            }
        } else {
            gaps.add(dataGap("INDUSTRY_CLASSIFICATION", "行业归属", "未匹配到行业名称对应的 Mock 行业数据", true,
                    "UNKNOWN", "无法评估行业环境、周期与竞争格局，也无法进行行业基准对比",
                    "人工确认企业实际主营业务与国别行业分类后重跑"));
            supplementRequests.add(supplement("INDUSTRY_CLASSIFICATION", "行业归属",
                    "行业未识别，缺少行业数据依据", "补充企业实际主营业务、行业分类或行业研究报告"));
        }

        if (resolved) {
            BigDecimal latestGrowth = profile.growthRates().isEmpty()
                    ? null : profile.growthRates().getLast();
            if ((latestGrowth != null && latestGrowth.signum() < 0) || "衰退期".equals(profile.cycleStage())) {
                findings.add(finding("IND_INDUSTRY_DECLINE", "INDUSTRY", "HIGH", "行业处于下行或衰退阶段",
                        "行业【" + profile.industryName() + "】最新增速为 "
                                + (latestGrowth == null ? "未知" : latestGrowth.toPlainString() + "%")
                                + "，周期阶段为【" + profile.cycleStage() + "】，行业系统性风险上升",
                        "CONFIRMED", new BigDecimal("0.85"), evidenceRefs,
                        "关注行业下行对企业订单与现金流的影响，审慎确定授信额度与期限"));
            }
            if ("限制".equals(profile.policyOrientation())) {
                findings.add(finding("IND_POLICY_RESTRICTED", "INDUSTRY", "CRITICAL", "行业属于政策限制类",
                        "行业【" + profile.industryName() + "】政策导向为【限制】，需评估政策变化对持续经营的影响",
                        "CONFIRMED", new BigDecimal("0.90"), evidenceRefs,
                        "核实最新行业政策与监管要求，必要时提交政策合规审查"));
            }
            if (profile.concentrationCr5() != null
                    && profile.concentrationCr5().compareTo(HIGH_CONCENTRATION_THRESHOLD) > 0) {
                findings.add(finding("IND_HIGH_CONCENTRATION", "INDUSTRY", "LOW", "行业集中度较高",
                        "行业【" + profile.industryName() + "】CR5 为 " + profile.concentrationCr5().toPlainString()
                                + "%，头部企业议价能力强，中小企业竞争压力大",
                        "MODEL_INFERENCE", new BigDecimal("0.65"), evidenceRefs,
                        "结合企业市场份额与客户结构评估其竞争地位"));
            }
            compareWithBenchmark(data, benchmark, context, findings, gaps, evidenceRefs);
        }

        data.setEvidenceCount(evidenceRefs.size());
        BigDecimal completeness = !resolved ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : benchmark == null ? new BigDecimal("0.50")
                : BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        BigDecimal confidence = !resolved ? new BigDecimal("0.40")
                : findings.isEmpty() ? new BigDecimal("0.85") : new BigDecimal("0.75");
        String summary = resolved
                ? "行业分析完成（Mock 行业数据）：行业【" + profile.industryName() + "（" + profile.industryCode()
                    + "）】，政策导向 " + profile.policyOrientation() + "，周期阶段 " + profile.cycleStage()
                    + "，集中度 CR5 " + safe(profile.concentrationCr5()) + "%；风险线索 " + findings.size()
                    + " 条，数据缺口 " + gaps.size() + " 项"
                : "行业分析完成但行业未识别：行业归属待人工确认，无法开展行业环境与基准对比";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    /** 企业与行业基准对比：上游财务数据缺失时只记缺口，不阻断执行 */
    private void compareWithBenchmark(IndustryExpertData data, IndustryBenchmarkSnapshot benchmark,
                                      AgentContext context, List<AgentFinding> findings,
                                      List<AgentDataGap> gaps, List<EvidenceRef> evidenceRefs) {
        if (benchmark == null) {
            return;
        }
        FinancialIndicators indicators = financialIndicators(context);
        BigDecimal enterpriseMargin = indicators.grossMargin();
        BigDecimal enterpriseLeverage = indicators.leverage();
        if (enterpriseMargin == null && enterpriseLeverage == null) {
            gaps.add(dataGap("FINANCIAL_INDICATORS", "企业财务指标", "未获取到上游财务分析结果，未开展行业基准对比",
                    true, "NOT_PROVIDED", "行业对比仅包含行业侧数据，缺少企业侧结论",
                    "待财务分析 Agent 完成后增量重跑"));
            return;
        }
        data.setEnterpriseGrossMargin(enterpriseMargin);
        data.setEnterpriseAssetLiabilityRatio(enterpriseLeverage);

        if (benchmark.grossMarginMedian() != null) {
            if (enterpriseMargin == null) {
                gaps.add(dataGap("GROSS_MARGIN", "企业毛利率", "未获取到企业毛利率，跳过毛利率行业对比", true,
                        "NOT_PROVIDED", "无法判断企业盈利能力在行业中的位置", "待财务分析完成后增量重跑"));
            } else {
                BigDecimal gap = enterpriseMargin.subtract(benchmark.grossMarginMedian())
                        .setScale(2, RoundingMode.HALF_UP);
                data.setGrossMarginGap(gap);
                if (gap.compareTo(MARGIN_GAP_THRESHOLD) < 0) {
                    findings.add(finding("IND_MARGIN_BELOW_BENCHMARK", "INDUSTRY", "MEDIUM",
                            "毛利率低于行业基准",
                            "企业毛利率 " + enterpriseMargin.toPlainString() + "%，低于行业中位数 "
                                    + benchmark.grossMarginMedian().toPlainString() + "% 达 "
                                    + gap.abs().toPlainString() + " 个百分点",
                            "NEEDS_VERIFICATION", new BigDecimal("0.75"), evidenceRefs,
                            "核实收入成本结构与产品竞争力，关注持续盈利能力"));
                }
            }
        }

        if (benchmark.assetLiabilityRatioMedian() != null) {
            if (enterpriseLeverage == null) {
                gaps.add(dataGap("ASSET_LIABILITY_RATIO", "企业资产负债率", "未获取到企业资产负债率，跳过杠杆对比",
                        true, "NOT_PROVIDED", "无法判断企业杠杆水平在行业中的位置",
                        "待财务分析完成后增量重跑"));
            } else {
                BigDecimal gap = enterpriseLeverage.subtract(benchmark.assetLiabilityRatioMedian())
                        .setScale(2, RoundingMode.HALF_UP);
                data.setAssetLiabilityGap(gap);
                if (gap.compareTo(LEVERAGE_GAP_THRESHOLD) > 0) {
                    findings.add(finding("IND_LEVERAGE_ABOVE_BENCHMARK", "INDUSTRY", "MEDIUM",
                            "资产负债率高于行业基准",
                            "企业资产负债率 " + enterpriseLeverage.toPlainString() + "%，高于行业中位数 "
                                    + benchmark.assetLiabilityRatioMedian().toPlainString() + "% 达 "
                                    + gap.toPlainString() + " 个百分点",
                            "NEEDS_VERIFICATION", new BigDecimal("0.75"), evidenceRefs,
                            "核实有息负债结构与到期分布，评估偿债压力"));
                }
            }
        }
    }

    private Map<String, Object> industryParams(AgentContext context) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("industryName", context.getEnterpriseProfile() == null ? ""
                : nullToEmpty(context.getEnterpriseProfile().getIndustryName()));
        params.put("businessScope", context.getEnterpriseProfile() == null ? ""
                : nullToEmpty(context.getEnterpriseProfile().getBusinessScope()));
        params.put("enterpriseName", context.getEnterpriseProfile() == null ? ""
                : nullToEmpty(context.getEnterpriseProfile().getEnterpriseName()));
        params.put("enterpriseId", context.getEnterpriseId() == null ? "" : String.valueOf(context.getEnterpriseId()));
        return params;
    }

    /** 工具登记的证据ID随 ToolResult 返回，直接引用，避免依赖执行前构建的上下文证据列表 */
    private void addToolEvidenceRef(List<EvidenceRef> evidenceRefs, ToolResult<?> toolResult,
                                    String sourceReference, String dataPeriod, String excerpt) {
        List<String> evidenceIds = toolResult.getEvidenceIds();
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return;
        }
        addEvidenceRef(evidenceRefs, Long.valueOf(evidenceIds.getFirst()), toolResult.getSource(),
                sourceReference, dataPeriod, excerpt);
    }

    /**
     * 读取上游财务指标：直接对象与断点恢复后的 Map 两种形态都兼容。
     * 财务分析输出中的比率是小数（0.35 表示 35%），这里统一换算为百分数以便与行业基准比较。
     */
    private FinancialIndicators financialIndicators(AgentContext context) {
        Map<String, Object> upstream = context.getUpstreamResults();
        if (upstream == null) {
            return new FinancialIndicators(null, null);
        }
        Object data = unwrap(upstream.get(UPSTREAM_FINANCIAL_ANALYSIS));
        if (data instanceof FinancialAnalysisData analysis) {
            List<FinancialPeriodIndicator> indicators = analysis.getPeriodIndicators();
            FinancialPeriodIndicator latest = indicators == null || indicators.isEmpty()
                    ? null : indicators.getLast();
            return new FinancialIndicators(percent(latest == null ? null : latest.getGrossMargin()),
                    percent(latest == null ? null : latest.getDebtAssetRatio()));
        }
        if (data instanceof Map<?, ?> map) {
            Object periods = map.get("periodIndicators");
            if (periods instanceof List<?> list && !list.isEmpty() && list.getLast() instanceof Map<?, ?> latest) {
                return new FinancialIndicators(percent(decimal(latest, "grossMargin", "grossMarginRate")),
                        percent(decimal(latest, "debtAssetRatio", "assetLiabilityRatio", "debtRatio")));
            }
            return new FinancialIndicators(percent(decimal(map, "grossMargin", "grossMarginRate")),
                    percent(decimal(map, "debtAssetRatio", "assetLiabilityRatio", "debtRatio")));
        }
        return new FinancialIndicators(null, null);
    }

    private BigDecimal percent(BigDecimal rate) {
        return rate == null ? null : rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 兼容直接传递 AgentResult 与断点恢复后反序列化为 Map 两种形态 */
    private Object unwrap(Object value) {
        if (value instanceof AgentResult<?> result) {
            return result.getData();
        }
        if (value instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return value;
    }

    private BigDecimal decimal(Map<?, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return new BigDecimal(text.trim());
                } catch (NumberFormatException ignored) {
                    // 非数值字段直接跳过，按缺失处理
                }
            }
        }
        return null;
    }

    private void addEvidenceRef(List<EvidenceRef> evidenceRefs, Long evidenceId, String sourceSystem,
                                String sourceReference, String dataPeriod, String excerpt) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(String.valueOf(evidenceId));
        ref.setEvidenceType(EvidenceType.INDUSTRY_RESEARCH.name());
        ref.setSourceSystem(sourceSystem);
        ref.setSourceReference(sourceReference);
        ref.setDataPeriod(dataPeriod);
        ref.setExcerpt(excerpt);
        evidenceRefs.add(ref);
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private AgentResult<IndustryExpertData> result(AgentContext context, IndustryExpertData data,
                                                    List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                    List<AgentSupplementRequest> supplementRequests,
                                                    List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                    BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<IndustryExpertData> result = new AgentResult<>();
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
        gap.setSourceType(EvidenceType.INDUSTRY_RESEARCH.name());
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

    private String safe(BigDecimal value) {
        return value == null ? "未知" : value.toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static AgentDefinition buildDefinition() {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode(AGENT_CODE);
        definition.setAgentName("行业专家 Agent");
        definition.setDescription("识别企业所属行业，分析行业环境、政策、周期与集中度，并对比企业指标与行业基准");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("SUBJECT_VERIFICATION", "FINANCIAL_ANALYSIS"));
        definition.setRequiredTools(List.of(TOOL_INDUSTRY_PROFILE, TOOL_INDUSTRY_BENCHMARK));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    /** 上游财务指标，比率已换算为百分数 */
    private record FinancialIndicators(BigDecimal grossMargin, BigDecimal leverage) {
    }
}
