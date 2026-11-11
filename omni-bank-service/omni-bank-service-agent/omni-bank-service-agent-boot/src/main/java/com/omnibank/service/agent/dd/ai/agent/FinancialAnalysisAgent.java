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
import com.omnibank.service.agent.dd.tool.impl.BankStatementSnapshot;
import com.omnibank.service.agent.dd.tool.impl.FinancialStatementsSnapshot;
import com.omnibank.service.agent.dd.tool.impl.TaxDataSnapshot;
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
 * Agent 02 财务分析：基于确定性规则计算盈利能力、偿债能力、营运能力与现金流指标，
 * 并对纳税申报收入、银行流水与报表收入做勾稽。指标计算全部由 Java 完成，不调用大模型。
 */
@Component
@RequiredArgsConstructor
public class FinancialAnalysisAgent implements CreditAgent<Void, FinancialAnalysisData> {

    public static final String AGENT_CODE = "FINANCIAL_ANALYSIS";

    private static final String TOOL_FINANCIAL_STATEMENTS = "financial.getFinancialStatements";

    private static final String TOOL_TAX_DATA = "financial.getTaxData";

    private static final String TOOL_BANK_STATEMENTS = "financial.getBankStatements";

    private static final BigDecimal HIGH_LEVERAGE_THRESHOLD = new BigDecimal("0.70");

    private static final BigDecimal LOW_MARGIN_THRESHOLD = new BigDecimal("0.15");

    private static final BigDecimal TAX_REVENUE_DEVIATION_THRESHOLD = new BigDecimal("0.10");

    private static final BigDecimal REVENUE_DECLINE_THRESHOLD = new BigDecimal("-0.20");

    private static final BigDecimal BANK_INFLOW_COVERAGE_THRESHOLD = new BigDecimal("0.60");

    private static final BigDecimal TREND_THRESHOLD = new BigDecimal("0.05");

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "财务分析 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<FinancialAnalysisData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        FinancialAnalysisData data = new FinancialAnalysisData();

        if (context.isMockMode()) {
            warnings.add("当前证据来自 Mock Adapter（isMock=true），结论仅用于链路验证");
        }

        String creditCode = resolveCreditCode(context);
        BigDecimal applyAmount = resolveApplyAmount(context);
        data.setCreditCode(creditCode);
        data.setEnterpriseName(context.getEnterpriseProfile() == null
                ? null : context.getEnterpriseProfile().getEnterpriseName());

        if (creditCode == null) {
            gaps.add(dataGap("MULTI_PERIOD_FINANCIAL_STATEMENT", "多期财务报表",
                    "缺少统一社会信用代码，无法获取财务报表", true, "NOT_PROVIDED",
                    "无法开展财务分析", "补充企业统一社会信用代码后重新分析"));
            supplementRequests.add(supplement("MULTI_PERIOD_FINANCIAL_STATEMENT", "多期财务报表",
                    "缺少企业统一社会信用代码", "补充企业统一社会信用代码"));
            missingItems.add("未获取企业统一社会信用代码，无法获取财务报表");
            data.setMissingItems(missingItems);
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), BigDecimal.ZERO, "财务分析未完成：缺少企业统一社会信用代码");
        }

        ToolResult<FinancialStatementsSnapshot> statementsResult = call(TOOL_FINANCIAL_STATEMENTS,
                request(context, toolParams(creditCode, context, applyAmount)));
        warnIfMock(TOOL_FINANCIAL_STATEMENTS, statementsResult, warnings);
        List<FinancialStatementsSnapshot.StatementPeriod> periods = statementsResult.isSuccess()
                && statementsResult.getData() != null
                ? statementsResult.getData().periods() : List.of();
        if (statementsResult.isSuccess() && statementsResult.getData() != null) {
            statementsResult.getData().evidences().forEach(ref -> evidenceRefs.add(toEvidenceRef(ref)));
        }

        List<FinancialPeriodIndicator> indicators = new ArrayList<>();
        FinancialStatementsSnapshot.StatementPeriod previous = null;
        for (FinancialStatementsSnapshot.StatementPeriod period : periods) {
            indicators.add(indicator(period, previous));
            previous = period;
        }
        FinancialPeriodIndicator latestIndicator = indicators.isEmpty()
                ? null : indicators.get(indicators.size() - 1);
        FinancialStatementsSnapshot.StatementPeriod latestPeriod = periods.isEmpty()
                ? null : periods.get(periods.size() - 1);

        Map<String, String> trends = new LinkedHashMap<>();
        if (indicators.size() >= 2) {
            FinancialPeriodIndicator current = indicators.get(indicators.size() - 1);
            FinancialPeriodIndicator prior = indicators.get(indicators.size() - 2);
            trends.put("营业收入", direction(current.getRevenue(), prior.getRevenue()));
            trends.put("净利润", direction(current.getNetProfit(), prior.getNetProfit()));
            trends.put("资产负债率", direction(current.getDebtAssetRatio(), prior.getDebtAssetRatio()));
            trends.put("毛利率", direction(current.getGrossMargin(), prior.getGrossMargin()));
            trends.put("流动比率", direction(current.getCurrentRatio(), prior.getCurrentRatio()));
        }

        data.setStatementPeriods(periods.size());
        data.setPeriodIndicators(indicators);
        data.setIndicatorTrends(trends);
        if (latestPeriod != null) {
            data.setLatestRevenue(latestPeriod.revenue());
            data.setLatestNetAssets(latestPeriod.netAssets());
            data.setLatestNetProfit(latestPeriod.netProfit());
        }
        BigDecimal latestRevenue = data.getLatestRevenue();

        ToolResult<TaxDataSnapshot> taxResult = call(TOOL_TAX_DATA,
                request(context, toolParams(creditCode, context, applyAmount, latestRevenue)));
        warnIfMock(TOOL_TAX_DATA, taxResult, warnings);
        if (taxResult.isSuccess() && taxResult.getData() != null) {
            taxResult.getData().evidences().forEach(ref -> evidenceRefs.add(toEvidenceRef(ref)));
        }

        ToolResult<BankStatementSnapshot> bankResult = call(TOOL_BANK_STATEMENTS,
                request(context, toolParams(creditCode, context, applyAmount, latestRevenue)));
        warnIfMock(TOOL_BANK_STATEMENTS, bankResult, warnings);
        if (bankResult.isSuccess() && bankResult.getData() != null) {
            bankResult.getData().evidences().forEach(ref -> evidenceRefs.add(toEvidenceRef(ref)));
        }

        List<FinancialReconciliation> reconciliations = new ArrayList<>();
        BigDecimal taxDeviation = null;
        if (taxResult.isSuccess() && taxResult.getData() != null && latestRevenue != null
                && latestRevenue.signum() > 0) {
            BigDecimal declaredRevenue = taxResult.getData().declaredRevenue();
            if (declaredRevenue != null) {
                taxDeviation = DdDeterministicMockData.divide(declaredRevenue.subtract(latestRevenue),
                        latestRevenue, 4);
                boolean consistent = taxDeviation.abs().compareTo(TAX_REVENUE_DEVIATION_THRESHOLD) <= 0;
                reconciliations.add(new FinancialReconciliation("TAX_REVENUE_VS_STATEMENT",
                        "纳税申报收入与报表收入", latestRevenue, declaredRevenue, taxDeviation, consistent,
                        "纳税申报收入较报表收入偏离 " + percent(taxDeviation) + "，经验容忍区间 ±10%"));
            }
        }
        if (bankResult.isSuccess() && bankResult.getData() != null && latestRevenue != null
                && latestRevenue.signum() > 0) {
            BigDecimal inflow = bankResult.getData().operatingInflow();
            if (inflow != null) {
                BigDecimal coverage = DdDeterministicMockData.divide(inflow, latestRevenue, 4);
                boolean consistent = coverage.compareTo(BANK_INFLOW_COVERAGE_THRESHOLD) >= 0;
                reconciliations.add(new FinancialReconciliation("BANK_INFLOW_VS_REVENUE",
                        "银行流水经营流入/报表收入", latestRevenue, inflow, coverage, consistent,
                        "银行流水经营流入覆盖报表收入 " + percent(coverage) + "，经验下限 60%"));
            }
        }
        data.setReconciliations(reconciliations);

        if (latestIndicator != null) {
            addSolvencyFindings(findings, latestIndicator, evidenceRefs);
            addGrowthFindings(findings, latestIndicator, evidenceRefs);
        }
        if (taxDeviation != null && taxDeviation.abs().compareTo(TAX_REVENUE_DEVIATION_THRESHOLD) > 0) {
            findings.add(finding("FIN_TAX_REVENUE_MISMATCH", "FINANCIAL_QUALITY", "HIGH", "纳税申报收入与报表收入偏离较大",
                    "纳税申报收入较报表收入偏离 " + percent(taxDeviation) + "，超过 10% 容忍区间，需核实收入确认口径",
                    "NEEDS_VERIFICATION", new BigDecimal("0.86"), evidenceRefs,
                    "核对纳税申报表、财务报表与销售合同，确认收入差异原因"));
        }

        if (periods.size() < 2) {
            missingItems.add("仅取得 " + periods.size() + " 期财务报表，无法开展多期趋势分析");
            gaps.add(dataGap("MULTI_PERIOD_FINANCIAL_STATEMENT", "多期财务报表",
                    "仅取得 " + periods.size() + " 期财务报表，无法计算同比指标", true, "NOT_PROVIDED",
                    "无法判断指标趋势与异常波动", "补充最近 3 个完整会计年度财务报表"));
            supplementRequests.add(supplement("MULTI_PERIOD_FINANCIAL_STATEMENT", "多期财务报表",
                    "财务分析仅取得 " + periods.size() + " 期报表", "补充最近 3 个完整会计年度财务报表"));
        }
        if (!taxResult.isSuccess()) {
            missingItems.add("未获取纳税申报数据，无法执行税务与报表勾稽");
        }
        if (!bankResult.isSuccess()) {
            missingItems.add("未获取银行流水，无法校验经营流入与报表收入的匹配度");
            gaps.add(dataGap("BANK_STATEMENT", "银行流水",
                    "未获取近 12 个月银行流水", false, "NOT_PROVIDED",
                    "无法校验经营流水与报表收入的匹配度", "补充近 12 个月银行流水"));
            supplementRequests.add(supplement("BANK_STATEMENT", "银行流水",
                    "财务分析缺少银行流水", "补充近 12 个月银行流水"));
        }

        data.setMissingItems(missingItems);
        data.setRiskFlags(findings.stream().map(AgentFinding::getFindingCode).toList());
        data.setEvidenceCount(evidenceRefs.size());

        int present = 0;
        if (periods.size() >= 2) {
            present++;
        }
        if (taxResult.isSuccess()) {
            present++;
        }
        if (bankResult.isSuccess()) {
            present++;
        }
        BigDecimal completeness = BigDecimal.valueOf(present)
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = findings.isEmpty()
                ? (gaps.isEmpty() ? new BigDecimal("0.90") : new BigDecimal("0.75"))
                : new BigDecimal("0.70");
        String summary = "财务分析完成：覆盖 " + periods.size() + " 期报表"
                + (data.getLatestRevenue() == null ? ""
                : "，最新期营业收入 " + plain(data.getLatestRevenue()) + " 元")
                + (latestIndicator == null || latestIndicator.getDebtAssetRatio() == null ? ""
                : "，资产负债率 " + percent(latestIndicator.getDebtAssetRatio()))
                + "；勾稽结果 " + reconciliations.size() + " 项，风险线索 " + findings.size()
                + " 条，数据缺口 " + gaps.size() + " 项";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    private void addSolvencyFindings(List<AgentFinding> findings, FinancialPeriodIndicator indicator,
                                     List<EvidenceRef> evidenceRefs) {
        if (indicator.getDebtAssetRatio() != null
                && indicator.getDebtAssetRatio().compareTo(HIGH_LEVERAGE_THRESHOLD) > 0) {
            findings.add(finding("FIN_HIGH_LEVERAGE", "SOLVENCY", "HIGH", "资产负债率偏高",
                    "最新期资产负债率 " + percent(indicator.getDebtAssetRatio()) + "，超过 70% 经验阈值",
                    "CONFIRMED", new BigDecimal("0.88"), evidenceRefs,
                    "核实负债结构、或有负债与本次新增负债的偿付安排"));
        }
        if (indicator.getOperatingCashFlow() != null && indicator.getOperatingCashFlow().signum() < 0) {
            findings.add(finding("FIN_NEGATIVE_OCF", "CASH_FLOW", "HIGH", "经营活动现金流为负",
                    "最新期经营活动现金流净额为 " + plain(indicator.getOperatingCashFlow())
                            + " 元，经营获现能力不足",
                    "CONFIRMED", new BigDecimal("0.87"), evidenceRefs,
                    "核实经营现金流转负原因及后续现金流改善安排"));
        }
    }

    private void addGrowthFindings(List<AgentFinding> findings, FinancialPeriodIndicator indicator,
                                   List<EvidenceRef> evidenceRefs) {
        if (indicator.getRevenueGrowthRate() != null
                && indicator.getRevenueGrowthRate().compareTo(REVENUE_DECLINE_THRESHOLD) < 0) {
            findings.add(finding("FIN_REVENUE_DECLINE", "GROWTH", "MEDIUM", "营业收入同比明显下滑",
                    "最新期营业收入同比下降 " + percent(indicator.getRevenueGrowthRate().abs())
                            + "，超过 20% 经验阈值",
                    "CONFIRMED", new BigDecimal("0.82"), evidenceRefs,
                    "核实收入下滑原因、订单储备与持续经营能力"));
        }
        if (indicator.getGrossMargin() != null
                && indicator.getGrossMargin().compareTo(LOW_MARGIN_THRESHOLD) < 0) {
            findings.add(finding("FIN_LOW_MARGIN", "PROFITABILITY", "MEDIUM", "毛利率低于行业经验值",
                    "最新期毛利率 " + percent(indicator.getGrossMargin()) + "，低于 15% 行业经验值",
                    "CONFIRMED", new BigDecimal("0.75"), evidenceRefs,
                    "核实成本结构与定价能力，关注毛利率持续下行风险"));
        }
    }

    private FinancialPeriodIndicator indicator(FinancialStatementsSnapshot.StatementPeriod period,
                                               FinancialStatementsSnapshot.StatementPeriod previous) {
        FinancialPeriodIndicator indicator = new FinancialPeriodIndicator();
        indicator.setPeriod(period.period());
        indicator.setRevenue(DdDeterministicMockData.money(period.revenue()));
        indicator.setOperatingCashFlow(DdDeterministicMockData.money(period.operatingCashFlow()));
        indicator.setNetProfit(DdDeterministicMockData.money(period.netProfit()));
        indicator.setCurrentRatio(DdDeterministicMockData.divide(period.currentAssets(),
                period.currentLiabilities(), 2));
        BigDecimal quickAssets = period.currentAssets().subtract(period.inventory());
        indicator.setQuickRatio(DdDeterministicMockData.divide(quickAssets, period.currentLiabilities(), 2));
        indicator.setDebtAssetRatio(DdDeterministicMockData.divide(period.totalLiabilities(),
                period.totalAssets(), 4));
        indicator.setGrossMargin(DdDeterministicMockData.divide(
                period.revenue().subtract(period.costOfSales()), period.revenue(), 4));
        indicator.setNetMargin(DdDeterministicMockData.divide(period.netProfit(), period.revenue(), 4));
        indicator.setRoe(DdDeterministicMockData.divide(period.netProfit(), period.netAssets(), 4));
        indicator.setEbitdaInterestCoverage(DdDeterministicMockData.divide(period.ebitda(),
                period.interestExpense(), 2));
        indicator.setOcfShortDebtRatio(DdDeterministicMockData.divide(period.operatingCashFlow(),
                period.shortTermDebt(), 4));
        BigDecimal averageReceivable = previous == null ? period.accountsReceivable()
                : period.accountsReceivable().add(previous.accountsReceivable())
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        indicator.setArTurnoverDays(DdDeterministicMockData.divide(
                averageReceivable.multiply(BigDecimal.valueOf(360)), period.revenue(), 2));
        BigDecimal averageInventory = previous == null ? period.inventory()
                : period.inventory().add(previous.inventory())
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        indicator.setInventoryTurnoverDays(DdDeterministicMockData.divide(
                averageInventory.multiply(BigDecimal.valueOf(360)), period.costOfSales(), 2));
        if (previous != null) {
            indicator.setRevenueGrowthRate(DdDeterministicMockData.divide(
                    period.revenue().subtract(previous.revenue()), previous.revenue(), 4));
            indicator.setNetProfitGrowthRate(DdDeterministicMockData.divide(
                    period.netProfit().subtract(previous.netProfit()), previous.netProfit().abs(), 4));
        }
        return indicator;
    }

    private Map<String, Object> toolParams(String creditCode, AgentContext context, BigDecimal applyAmount) {
        return toolParams(creditCode, context, applyAmount, null);
    }

    private Map<String, Object> toolParams(String creditCode, AgentContext context, BigDecimal applyAmount,
                                           BigDecimal latestRevenue) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("creditCode", creditCode);
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        if (applyAmount != null) {
            params.put("applyAmount", applyAmount);
        }
        if (latestRevenue != null) {
            params.put("latestRevenue", latestRevenue);
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

    private String direction(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return "数据不足";
        }
        BigDecimal change = DdDeterministicMockData.divide(current.subtract(previous), previous.abs(), 4);
        if (change.compareTo(TREND_THRESHOLD) > 0) {
            return "上升";
        }
        if (change.compareTo(TREND_THRESHOLD.negate()) < 0) {
            return "下降";
        }
        return "基本稳定";
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

    private AgentResult<FinancialAnalysisData> result(AgentContext context, FinancialAnalysisData data,
                                                      List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                      List<AgentSupplementRequest> supplementRequests,
                                                      List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                      BigDecimal confidence, BigDecimal completeness,
                                                      String summary) {
        AgentResult<FinancialAnalysisData> result = new AgentResult<>();
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
        gap.setSourceType(EvidenceType.FINANCIAL_STATEMENT.name());
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
        definition.setAgentName("财务分析 Agent");
        definition.setDescription("计算盈利、偿债、营运与现金流指标，校验税务、流水与报表的勾稽关系");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("SUBJECT_VERIFICATION", "DOCUMENT_RECOGNITION"));
        definition.setRequiredTools(List.of(TOOL_FINANCIAL_STATEMENTS, TOOL_TAX_DATA, TOOL_BANK_STATEMENTS));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }
}
