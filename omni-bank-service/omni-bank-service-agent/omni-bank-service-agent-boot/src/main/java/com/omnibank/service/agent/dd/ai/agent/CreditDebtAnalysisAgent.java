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
import com.omnibank.service.agent.dd.support.DdDeterministicMockData;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.impl.DebtMaturitySnapshot;
import com.omnibank.service.agent.dd.tool.impl.EnterpriseCreditReportSnapshot;
import com.omnibank.service.agent.dd.tool.impl.ToolEvidenceRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 04 征信债务分析：基于征信报告与债务期限结构计算使用率、逾期率、担保比、短债占比与债务负担，
 * 营业收入优先取上游财务分析结果，取不到时回落申请金额并记录数据缺口。
 */
@Component
@RequiredArgsConstructor
public class CreditDebtAnalysisAgent implements CreditAgent<Void, CreditDebtAnalysisData> {

    public static final String AGENT_CODE = "CREDIT_DEBT_ANALYSIS";

    private static final String TOOL_CREDIT_REPORT = "credit.getEnterpriseCreditReport";

    private static final String TOOL_DEBT_MATURITY = "credit.getDebtMaturity";

    private static final String UPSTREAM_FINANCIAL_AGENT = "FINANCIAL_ANALYSIS";

    private static final BigDecimal HIGH_UTILIZATION_THRESHOLD = new BigDecimal("0.80");

    private static final BigDecimal HIGH_GUARANTEE_THRESHOLD = new BigDecimal("0.50");

    private static final BigDecimal MATURITY_CONCENTRATION_THRESHOLD = new BigDecimal("0.50");

    private static final BigDecimal DEBT_BURDEN_THRESHOLD = new BigDecimal("1.00");

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "征信债务 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<CreditDebtAnalysisData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        CreditDebtAnalysisData data = new CreditDebtAnalysisData();

        if (context.isMockMode()) {
            warnings.add("当前证据来自 Mock Adapter（isMock=true），结论仅用于链路验证");
        }

        String creditCode = resolveCreditCode(context);
        BigDecimal applyAmount = resolveApplyAmount(context);
        data.setCreditCode(creditCode);

        if (creditCode == null) {
            gaps.add(dataGap("CREDIT_REPORT_MISSING", "企业征信报告",
                    "缺少统一社会信用代码，无法获取征信报告", true, "NOT_PROVIDED",
                    "无法分析征信使用率、逾期与担保情况", "补充企业统一社会信用代码后重新分析"));
            supplementRequests.add(supplement("CREDIT_REPORT_MISSING", "企业征信报告",
                    "缺少企业统一社会信用代码", "补充企业统一社会信用代码"));
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), BigDecimal.ZERO, "征信债务分析未完成：缺少企业统一社会信用代码");
        }

        FinancialReference financial = upstreamFinancial(context);
        BigDecimal netAssets = financial.netAssets();

        ToolResult<EnterpriseCreditReportSnapshot> reportResult = call(TOOL_CREDIT_REPORT,
                request(context, reportParams(creditCode, context, applyAmount, netAssets)));
        warnIfMock(TOOL_CREDIT_REPORT, reportResult, warnings);
        boolean reportAvailable = reportResult.isSuccess() && reportResult.getData() != null;
        EnterpriseCreditReportSnapshot report = reportAvailable ? reportResult.getData() : null;
        if (reportAvailable) {
            report.evidences().forEach(ref -> evidenceRefs.add(toEvidenceRef(ref)));
        }

        ToolResult<DebtMaturitySnapshot> debtResult = call(TOOL_DEBT_MATURITY,
                request(context, debtParams(creditCode, context, applyAmount,
                        report == null ? null : report.usedCredit())));
        warnIfMock(TOOL_DEBT_MATURITY, debtResult, warnings);
        boolean debtAvailable = debtResult.isSuccess() && debtResult.getData() != null;
        DebtMaturitySnapshot debt = debtAvailable ? debtResult.getData() : null;
        if (debtAvailable) {
            debt.evidences().forEach(ref -> evidenceRefs.add(toEvidenceRef(ref)));
        }

        if (report == null) {
            gaps.add(dataGap("CREDIT_REPORT_MISSING", "企业征信报告",
                    "未获取到企业征信报告", true, "UNAVAILABLE",
                    "无法分析授信使用率、逾期与对外担保", "重新获取企业征信报告"));
            supplementRequests.add(supplement("CREDIT_REPORT_MISSING", "企业征信报告",
                    "征信债务分析缺少征信报告", "补充企业征信报告"));
        }

        if (report != null) {
            fillCreditReport(data, report);
            if (report.overdueCount() > 0) {
                findings.add(finding("CREDIT_OVERDUE", "CREDIT", "HIGH", "存在逾期记录",
                        "征信报告显示逾期 " + report.overdueCount() + " 次，逾期金额 "
                                + plain(report.overdueAmount()) + " 元，近 12 个月还款记录中逾期 "
                                + data.getOverdueRepaymentMonths() + " 期",
                        "CONFIRMED", new BigDecimal("0.90"), evidenceRefs,
                        "核实逾期发生时间、金额与当前还款状态，确认是否已结清"));
            }
            if (data.getCreditUtilization() != null
                    && data.getCreditUtilization().compareTo(HIGH_UTILIZATION_THRESHOLD) > 0) {
                findings.add(finding("CREDIT_HIGH_UTILIZATION", "CREDIT", "MEDIUM", "征信使用率偏高",
                        "已用授信占授信总额 " + percent(data.getCreditUtilization()) + "，超过 80% 经验阈值",
                        "CONFIRMED", new BigDecimal("0.84"), evidenceRefs,
                        "核实授信额度使用结构与后续融资空间"));
            }
            if (data.getGuaranteeToNetAssetsRatio() != null
                    && data.getGuaranteeToNetAssetsRatio().compareTo(HIGH_GUARANTEE_THRESHOLD) > 0) {
                findings.add(finding("CREDIT_HIGH_GUARANTEE", "GUARANTEE", "HIGH", "对外担保占净资产比例偏高",
                        "对外担保余额占净资产 " + percent(data.getGuaranteeToNetAssetsRatio())
                                + "，超过 50% 经验阈值，存在代偿风险",
                        "CONFIRMED", new BigDecimal("0.86"), evidenceRefs,
                        "核实被担保企业资质、担保期限与代偿可能性"));
            }
        }

        if (debt != null) {
            fillDebtMaturity(data, debt);
            if (data.getDueWithin6MonthsRatio() != null
                    && data.getDueWithin6MonthsRatio().compareTo(MATURITY_CONCENTRATION_THRESHOLD) > 0) {
                findings.add(finding("CREDIT_MATURITY_CONCENTRATION", "DEBT_MATURITY", "MEDIUM", "债务集中到期",
                        "未来 6 个月内到期金额占有息负债 " + percent(data.getDueWithin6MonthsRatio())
                                + "，超过 50% 经验阈值，存在集中偿付压力",
                        "CONFIRMED", new BigDecimal("0.83"), evidenceRefs,
                        "核实到期债务的续贷安排与还款资金来源"));
            }
        }

        BigDecimal revenue = financial.revenue();
        if (revenue == null) {
            if (applyAmount != null && applyAmount.signum() > 0) {
                revenue = applyAmount;
                gaps.add(dataGap("FINANCIAL_REVENUE", "营业收入",
                        "未取得上游财务分析的营业收入", false, "NOT_PROVIDED",
                        "债务/营收比以本次申请金额近似，可能存在偏差", "补充最近年度营业收入或上游财务分析结果"));
            } else {
                gaps.add(dataGap("FINANCIAL_REVENUE", "营业收入",
                        "未取得营业收入与申请金额", false, "NOT_PROVIDED",
                        "无法计算债务/营收比", "补充最近年度营业收入或上游财务分析结果"));
            }
        }
        data.setRevenueUsed(revenue);

        if (debt != null && revenue != null && revenue.signum() > 0) {
            BigDecimal newDebt = applyAmount == null ? BigDecimal.ZERO : applyAmount;
            data.setDebtToRevenueRatio(DdDeterministicMockData.divide(
                    debt.totalInterestBearingDebt().add(newDebt), revenue, 4));
            if (data.getDebtToRevenueRatio() != null
                    && data.getDebtToRevenueRatio().compareTo(DEBT_BURDEN_THRESHOLD) > 0) {
                findings.add(finding("CREDIT_DEBT_BURDEN", "DEBT_BURDEN", "HIGH", "债务负担超过营业收入",
                        "现有有息负债与本次申请金额合计占营业收入 "
                                + percent(data.getDebtToRevenueRatio()) + "，超过 100% 经验阈值",
                        "CONFIRMED", new BigDecimal("0.87"), evidenceRefs,
                        "结合现金流与盈利能力评估新增授信后的偿付能力"));
            }
        }

        data.setEvidenceCount(evidenceRefs.size());
        int present = 0;
        if (reportAvailable) {
            present++;
        }
        if (debtAvailable) {
            present++;
        }
        BigDecimal completeness = BigDecimal.valueOf(present)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = findings.isEmpty()
                ? (gaps.isEmpty() ? new BigDecimal("0.90") : new BigDecimal("0.75"))
                : new BigDecimal("0.70");
        String summary = "征信债务分析完成：" + (reportAvailable ? "已获取征信报告" : "征信报告缺失")
                + (data.getCreditUtilization() == null ? ""
                : "，征信使用率 " + percent(data.getCreditUtilization()))
                + (data.getOverdueCount() == null ? "" : "，逾期 " + data.getOverdueCount() + " 次")
                + (data.getTotalInterestBearingDebt() == null ? ""
                : "，有息负债 " + plain(data.getTotalInterestBearingDebt()) + " 元")
                + "；风险线索 " + findings.size() + " 条，数据缺口 " + gaps.size() + " 项";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    private void fillCreditReport(CreditDebtAnalysisData data, EnterpriseCreditReportSnapshot report) {
        data.setCreditLimit(report.creditLimit());
        data.setUsedCredit(report.usedCredit());
        data.setAvailableCredit(report.availableCredit());
        data.setCreditUtilization(DdDeterministicMockData.divide(report.usedCredit(), report.creditLimit(), 4));
        data.setOverdueCount(report.overdueCount());
        data.setOverdueAmount(report.overdueAmount());
        data.setOverdueRate(DdDeterministicMockData.divide(report.overdueAmount(), report.usedCredit(), 4));
        data.setExternalGuaranteeBalance(report.externalGuaranteeBalance());
        data.setGuaranteeToNetAssetsRatio(DdDeterministicMockData.divide(report.externalGuaranteeBalance(),
                report.netAssets(), 4));
        data.setRepaymentRecordCount(report.repaymentRecords() == null ? 0 : report.repaymentRecords().size());
        data.setOverdueRepaymentMonths(report.repaymentRecords() == null ? 0 : (int) report.repaymentRecords()
                .stream().filter(record -> "OVERDUE".equals(record.status())).count());
    }

    private void fillDebtMaturity(CreditDebtAnalysisData data, DebtMaturitySnapshot debt) {
        data.setShortTermDebt(debt.shortTermDebt());
        data.setMidTermDebt(debt.midTermDebt());
        data.setLongTermDebt(debt.longTermDebt());
        data.setTotalInterestBearingDebt(debt.totalInterestBearingDebt());
        data.setDueWithin6Months(debt.dueWithin6Months());
        data.setDueWithin12Months(debt.dueWithin12Months());
        data.setShortTermDebtRatio(DdDeterministicMockData.divide(debt.shortTermDebt(),
                debt.totalInterestBearingDebt(), 4));
        data.setDueWithin6MonthsRatio(DdDeterministicMockData.divide(debt.dueWithin6Months(),
                debt.totalInterestBearingDebt(), 4));
    }

    /** 上游结果可能是 POJO，也可能是恢复执行时反序列化出的 Map，统一按字段名防御式读取 */
    private FinancialReference upstreamFinancial(AgentContext context) {
        Map<String, Object> upstream = context.getUpstreamResults();
        if (upstream == null || upstream.isEmpty()) {
            return new FinancialReference(null, null);
        }
        Object data = unwrapData(upstream.get(UPSTREAM_FINANCIAL_AGENT));
        if (data == null) {
            return new FinancialReference(null, null);
        }
        return new FinancialReference(decimalField(data, "latestRevenue"),
                decimalField(data, "latestNetAssets"));
    }

    private Object unwrapData(Object value) {
        if (value instanceof AgentResult<?> result) {
            return result.getData();
        }
        if (value instanceof Map<?, ?> map) {
            Object data = map.get("data");
            return data == null ? map : data;
        }
        return null;
    }

    private BigDecimal decimalField(Object data, String field) {
        if (data instanceof FinancialAnalysisData financial) {
            return "latestRevenue".equals(field) ? financial.getLatestRevenue() : financial.getLatestNetAssets();
        }
        if (data instanceof Map<?, ?> map) {
            return toDecimal(map.get(field));
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> reportParams(String creditCode, AgentContext context, BigDecimal applyAmount,
                                             BigDecimal netAssets) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("creditCode", creditCode);
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        if (applyAmount != null) {
            params.put("applyAmount", applyAmount);
        }
        if (netAssets != null) {
            params.put("netAssets", netAssets);
        }
        return params;
    }

    private Map<String, Object> debtParams(String creditCode, AgentContext context, BigDecimal applyAmount,
                                           BigDecimal usedCredit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("creditCode", creditCode);
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        if (applyAmount != null) {
            params.put("applyAmount", applyAmount);
        }
        if (usedCredit != null) {
            params.put("usedCredit", usedCredit);
        }
        return params;
    }

    private ToolRequest request(AgentContext context, Map<String, Object> params) {
        return new ToolRequest(context.getTaskId(), params);
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private void warnIfMock(String toolCode, ToolResult<?> toolResult, List<String> warnings) {
        Object mock = toolResult.getMetadata() == null ? null : toolResult.getMetadata().get("mock");
        if (Boolean.TRUE.equals(mock) || toolRegistry.require(toolCode).getDefinition().isMock()) {
            warnings.add("工具【" + toolCode + "】返回 Mock 数据（来源 " + toolResult.getSource()
                    + "），结论仅用于链路验证");
        }
    }

    private EvidenceRef toEvidenceRef(ToolEvidenceRef snapshot) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(snapshot.evidenceId() == null ? null : String.valueOf(snapshot.evidenceId()));
        ref.setEvidenceType(snapshot.evidenceType());
        ref.setSourceSystem(snapshot.sourceSystem());
        ref.setSourceReference(snapshot.sourceReference());
        ref.setDataPeriod(snapshot.dataPeriod());
        ref.setExcerpt(snapshot.excerpt());
        return ref;
    }

    private String resolveCreditCode(AgentContext context) {
        String creditCode = context.getEnterpriseProfile() == null
                ? null : context.getEnterpriseProfile().getCreditCode();
        return creditCode == null || creditCode.isBlank() ? null : creditCode.trim();
    }

    private BigDecimal resolveApplyAmount(AgentContext context) {
        LoanApplicationVO application = context.getCreditApplication();
        return application == null ? null : application.getApplyAmount();
    }

    private String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "-";
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private AgentResult<CreditDebtAnalysisData> result(AgentContext context, CreditDebtAnalysisData data,
                                                       List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                       List<AgentSupplementRequest> supplementRequests,
                                                       List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                       BigDecimal confidence, BigDecimal completeness,
                                                       String summary) {
        AgentResult<CreditDebtAnalysisData> result = new AgentResult<>();
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
        gap.setSourceType(EvidenceType.CREDIT_REPORT.name());
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
        definition.setAgentName("征信债务 Agent");
        definition.setDescription("分析企业征信使用、逾期、对外担保与债务期限结构，评估集中到期与债务负担");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("SUBJECT_VERIFICATION"));
        definition.setRequiredTools(List.of(TOOL_CREDIT_REPORT, TOOL_DEBT_MATURITY));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    /** 上游财务分析摘要：营业收入用于债务/营收比，净资产用于对外担保比 */
    private record FinancialReference(BigDecimal revenue, BigDecimal netAssets) {
    }
}
