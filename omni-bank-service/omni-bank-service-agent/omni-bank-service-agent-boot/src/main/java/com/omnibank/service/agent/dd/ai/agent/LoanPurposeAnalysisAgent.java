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
import com.omnibank.service.agent.dd.tool.impl.LoanPurposeSnapshots;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 08 贷款用途合理性：识别用途分类，按业务场景分别测算资金需求，核验交易背景与资金流向，
 * 并分别评价第一、第二还款来源。金额、期限与用途判断全部由确定性规则完成，不输出最终审批金额。
 */
@Component
@RequiredArgsConstructor
public class LoanPurposeAnalysisAgent implements CreditAgent<Void, LoanPurposeAnalysisData> {

    public static final String AGENT_CODE = "LOAN_PURPOSE_ANALYSIS";

    private static final String TOOL_CLASSIFY = "loanPurpose.classifyLoanPurpose";

    private static final String TOOL_WORKING_CAPITAL = "loanPurpose.calculateWorkingCapitalDemand";

    private static final String TOOL_PROJECT = "loanPurpose.calculateProjectFundingDemand";

    private static final String TOOL_CONTRACT = "loanPurpose.analyzeContract";

    private static final String TOOL_FUND_FLOW = "loanPurpose.analyzeFundFlow";

    private static final String UPSTREAM_FINANCIAL_ANALYSIS = "FINANCIAL_ANALYSIS";

    private static final BigDecimal EXCESSIVE_THRESHOLD = new BigDecimal("1.20");

    private static final BigDecimal INSUFFICIENT_THRESHOLD = new BigDecimal("0.50");

    private static final BigDecimal KEY_RANGE_LOWER = new BigDecimal("0.80");

    private static final String APPROVAL_AMOUNT_NOTE = "AI 仅提供需求测算与合理性分析，不输出最终审批金额";

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "贷款用途合理性分析 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<LoanPurposeAnalysisData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        LoanPurposeAnalysisData data = new LoanPurposeAnalysisData();

        if (context.isMockMode()) {
            warnings.add("当前证据来自 Mock Adapter（isMock=true），结论仅用于链路验证");
        }
        warnings.add("需求测算公式与参数来自 Mock 配置，非本行现行制度公式，需以正式配置为准");

        String creditCode = resolveCreditCode(context);
        BigDecimal applyAmount = resolveApplyAmount(context);
        data.setCreditCode(creditCode);
        data.setEnterpriseName(context.getEnterpriseProfile() == null
                ? null : context.getEnterpriseProfile().getEnterpriseName());
        data.setApplyAmount(applyAmount);
        data.setApprovalAmountNote(APPROVAL_AMOUNT_NOTE);
        data.setSecondaryCannotReplacePrimary(true);
        if (context.getCreditApplication() != null) {
            LoanApplicationVO application = context.getCreditApplication();
            data.setApplyTerm(application.getApplyTerm());
            data.setTermUnit(application.getTermUnit());
            data.setProductName(application.getProductName());
            data.setLoanPurpose(application.getLoanPurpose());
        }

        if (creditCode == null) {
            gaps.add(dataGap("LOAN_PURPOSE_BASE_DATA", "贷款用途基础数据",
                    "缺少统一社会信用代码，无法开展用途分类与需求测算", true, "NOT_PROVIDED",
                    "无法分析用途合理性与资金需求", "补充企业统一社会信用代码后重新分析"));
            supplementRequests.add(supplement("LOAN_PURPOSE_BASE_DATA", "贷款用途基础数据",
                    "缺少企业统一社会信用代码", "补充企业统一社会信用代码与授信申请信息"));
            missingItems.add("未获取企业统一社会信用代码，贷款用途分析未完成");
            data.setMissingItems(missingItems);
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), BigDecimal.ZERO, "贷款用途分析未完成：缺少企业统一社会信用代码");
        }

        Map<String, Object> params = toolParams(context, creditCode);
        ToolRequest toolRequest = new ToolRequest(context.getTaskId(), params);

        ToolResult<LoanPurposeSnapshots.Classification> classifyResult = call(TOOL_CLASSIFY, toolRequest);
        warnIfMock(TOOL_CLASSIFY, classifyResult, warnings);
        String category = "OTHER";
        if (classifyResult.isSuccess() && classifyResult.getData() != null) {
            LoanPurposeSnapshots.Classification classification = classifyResult.getData();
            category = classification.purposeCategory();
            data.setPurposeCategory(category);
            data.setPurposeCategoryName(classification.categoryName());
            data.setBusinessScenario(classification.businessScenario());
            addToolEvidenceRef(evidenceRefs, classifyResult, EvidenceType.CALCULATION,
                    "LOAN_PURPOSE_CLASS:" + category, classification.dataPeriod(),
                    "贷款用途分类：" + classification.categoryName());
        } else {
            gaps.add(dataGap("LOAN_PURPOSE_CLASSIFICATION", "贷款用途分类", "用途分类工具未返回结果", true,
                    "UNAVAILABLE", "无法判断用途合理性", "重新执行用途分类或人工确认贷款用途"));
        }

        analyzePurposeReasonableness(context, data, category, findings, gaps, supplementRequests, evidenceRefs);
        analyzeDemand(data, toolRequest, category, applyAmount, evidenceRefs, warnings, gaps,
                supplementRequests, missingItems);
        analyzeContract(data, toolRequest, findings, gaps, evidenceRefs, warnings);
        analyzeFundFlow(data, toolRequest, findings, gaps, evidenceRefs, warnings);
        analyzeTerm(data, findings);
        analyzeRepayment(context, data, findings, gaps, supplementRequests, missingItems);

        data.setAnomalies(findings.stream().map(AgentFinding::getFindingCode).toList());
        data.setInvestigationSuggestions(investigationSuggestions(findings));
        data.setEvidenceCount(evidenceRefs.size());
        data.setMissingItems(missingItems);

        int present = 0;
        if (data.getMeasuredDemand() != null) {
            present++;
        }
        if (data.getContractCount() != null) {
            present++;
        }
        if (data.getFundFlowClues() != null) {
            present++;
        }
        if (!"UNKNOWN".equals(data.getPrimaryRepaymentAssessment())) {
            present++;
        }
        BigDecimal completeness = BigDecimal.valueOf(present)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = gaps.isEmpty() ? new BigDecimal("0.85") : new BigDecimal("0.72");
        String summary = "贷款用途分析完成：用途分类 " + data.getPurposeCategoryName()
                + "，申请金额 " + plain(applyAmount) + " 元"
                + (data.getMeasuredDemand() == null ? "" : "，测算需求 " + plain(data.getMeasuredDemand()) + " 元")
                + (data.getFundingGap() == null ? "" : "，资金缺口 " + plain(data.getFundingGap()) + " 元")
                + "，金额合理性 " + data.getAmountReasonableness()
                + "；风险线索 " + findings.size() + " 条，数据缺口 " + gaps.size() + " 项";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    private void analyzePurposeReasonableness(AgentContext context, LoanPurposeAnalysisData data, String category,
                                              List<AgentFinding> findings, List<AgentDataGap> gaps,
                                              List<AgentSupplementRequest> supplementRequests,
                                              List<EvidenceRef> evidenceRefs) {
        if ("OTHER".equals(category)) {
            data.setPurposeReasonableness("UNKNOWN");
            data.setPurposeReasonablenessReason("未识别到明确的贷款用途分类");
            findings.add(finding("LOAN_PURPOSE_UNCLASSIFIED", "PURPOSE", "MEDIUM", "贷款用途未识别",
                    "申请用途【" + nullToDash(data.getLoanPurpose()) + "】未匹配到预置用途分类，需人工确认用途",
                    "NEEDS_VERIFICATION", new BigDecimal("0.70"), evidenceRefs,
                    "人工确认贷款用途、交易背景与资金支付对象"));
            gaps.add(dataGap("LOAN_PURPOSE_DESCRIPTION", "贷款用途说明", "用途描述无法归类", true, "UNKNOWN",
                    "资金需求测算与政策匹配缺少场景依据", "补充明确的贷款用途说明与采购/项目背景材料"));
            supplementRequests.add(supplement("LOAN_PURPOSE_DESCRIPTION", "贷款用途说明",
                    "贷款用途无法归类", "补充贷款用途说明、合同或项目立项材料"));
            return;
        }
        String businessScope = context.getEnterpriseProfile() == null
                ? null : context.getEnterpriseProfile().getBusinessScope();
        if (businessScope == null || businessScope.isBlank()) {
            data.setPurposeReasonableness("NEEDS_VERIFICATION");
            data.setPurposeReasonablenessReason("未取得经营范围，无法比对企业主营与贷款用途");
            gaps.add(dataGap("BUSINESS_SCOPE", "企业经营范围", "未取得企业经营范围", true, "NOT_PROVIDED",
                    "无法判断贷款用途与企业经营情况是否匹配", "补充企业营业执照或经营范围信息"));
            supplementRequests.add(supplement("BUSINESS_SCOPE", "企业经营范围",
                    "贷款用途分析缺少经营范围", "补充营业执照或经营范围信息"));
            return;
        }
        boolean projectScenario = "PROJECT_CONSTRUCTION".equals(category) || "EQUIPMENT_PURCHASE".equals(category);
        boolean matched = projectScenario
                ? containsAny(businessScope, "制造", "生产", "建设", "工程", "设备", "加工")
                : containsAny(businessScope, "销售", "批发", "零售", "贸易", "服务", "生产", "制造", "经营");
        if (matched) {
            data.setPurposeReasonableness("RATIONAL");
            data.setPurposeReasonablenessReason("贷款用途与企业经营范围表述基本匹配");
        } else {
            data.setPurposeReasonableness("NEEDS_VERIFICATION");
            data.setPurposeReasonablenessReason("贷款用途与经营范围未发现直接对应关系");
            findings.add(finding("LOAN_PURPOSE_SCOPE_MISMATCH", "PURPOSE", "HIGH", "贷款用途与经营范围不匹配",
                    "申请用途【" + nullToDash(data.getLoanPurpose()) + "】与经营范围【" + businessScope
                            + "】未发现直接对应关系，需核实实际经营与资金用途",
                    "NEEDS_VERIFICATION", new BigDecimal("0.78"), evidenceRefs,
                    "核实企业实际主营业务、采购合同与资金支付对象"));
        }
    }

    private void analyzeDemand(LoanPurposeAnalysisData data, ToolRequest toolRequest, String category,
                               BigDecimal applyAmount, List<EvidenceRef> evidenceRefs, List<String> warnings,
                               List<AgentDataGap> gaps, List<AgentSupplementRequest> supplementRequests,
                               List<String> missingItems) {
        String toolCode = demandTool(category);
        if (toolCode == null) {
            gaps.add(dataGap("LOAN_PURPOSE_DEMAND", "资金需求测算",
                    "用途分类【" + data.getPurposeCategoryName() + "】暂无适用的配置化测算公式", true, "NOT_APPLICABLE",
                    "无法测算资金需求与资金缺口", "人工按行内制度选择对应测算模板"));
            supplementRequests.add(supplement("LOAN_PURPOSE_DEMAND", "资金需求测算",
                    "缺少适用测算公式", "明确业务场景后由人工按行内制度测算"));
            missingItems.add("未执行资金需求测算：无适用配置化公式");
            data.setAmountReasonableness("UNKNOWN");
            return;
        }
        ToolResult<LoanPurposeSnapshots.Demand> demandResult = call(toolCode, toolRequest);
        warnIfMock(toolCode, demandResult, warnings);
        if (!demandResult.isSuccess() || demandResult.getData() == null) {
            gaps.add(dataGap("LOAN_PURPOSE_DEMAND", "资金需求测算", "需求测算工具未返回结果", true, "UNAVAILABLE",
                    "无法测算资金需求与金额合理性", "重新执行需求测算"));
            data.setAmountReasonableness("UNKNOWN");
            return;
        }
        LoanPurposeSnapshots.Demand demand = demandResult.getData();
        data.setDemandType(demand.demandType());
        data.setMeasuredDemand(demand.measuredDemand());
        data.setDemandFormula(demand.formula());
        data.setDemandFormulaSource(demand.formulaSource());
        data.setDemandParameters(demand.parameters());
        addToolEvidenceRef(evidenceRefs, demandResult, EvidenceType.CALCULATION,
                "LOAN_PURPOSE_DEMAND:" + demand.demandType(), demand.dataPeriod(),
                "需求测算：" + plain(demand.measuredDemand()) + " 元，公式来源 " + demand.formulaSource());
        BigDecimal fundingGap = demand.measuredDemand() == null || applyAmount == null
                ? null : demand.measuredDemand().subtract(applyAmount).setScale(2, RoundingMode.HALF_UP);
        data.setFundingGap(fundingGap);
        if (demand.measuredDemand() != null) {
            data.setKeyAmountRangeMin(DdDeterministicMockData.money(
                    demand.measuredDemand().multiply(KEY_RANGE_LOWER)));
            data.setKeyAmountRangeMax(DdDeterministicMockData.money(demand.measuredDemand()));
        }
        String amountReasonableness = amountReasonableness(applyAmount, demand.measuredDemand());
        data.setAmountReasonableness(amountReasonableness);
        data.setAmountReasonablenessReason(amountReasonablenessReason(amountReasonableness, applyAmount,
                demand.measuredDemand(), fundingGap));
    }

    private void analyzeContract(LoanPurposeAnalysisData data, ToolRequest toolRequest,
                                 List<AgentFinding> findings, List<AgentDataGap> gaps,
                                 List<EvidenceRef> evidenceRefs, List<String> warnings) {
        ToolResult<LoanPurposeSnapshots.Contract> contractResult = call(TOOL_CONTRACT, toolRequest);
        warnIfMock(TOOL_CONTRACT, contractResult, warnings);
        if (!contractResult.isSuccess() || contractResult.getData() == null) {
            gaps.add(dataGap("LOAN_PURPOSE_CONTRACT", "合同订单发票", "合同订单核验工具未返回结果", false,
                    "UNAVAILABLE", "无法核验交易背景", "补充采购/销售合同、订单与发票"));
            return;
        }
        LoanPurposeSnapshots.Contract contract = contractResult.getData();
        data.setContractCount(contract.contractCount());
        data.setOrderCount(contract.orderCount());
        data.setInvoiceCount(contract.invoiceCount());
        data.setTotalContractAmount(contract.totalContractAmount());
        data.setContractPurposeConsistent(contract.purposeConsistent());
        data.setInvoicesMatched(contract.invoicesMatched());
        data.setCounterparties(contract.counterparties());
        data.setTransactionBackground(contract.purposeConsistent() && contract.invoicesMatched()
                ? "合同、订单与发票要素基本一致，交易背景仍需以原件核验为准"
                : "交易背景存在不一致项，需人工核实合同、发票与资金支付对象");
        addToolEvidenceRef(evidenceRefs, contractResult, EvidenceType.CUSTOMER_DOCUMENT,
                "LOAN_PURPOSE_CONTRACT", contract.dataPeriod(),
                "合同 " + contract.contractCount() + " 份，交易对手 " + String.join("、", contract.counterparties()));
        if (!contract.issues().isEmpty()) {
            findings.add(finding("LOAN_PURPOSE_CONTRACT_MISMATCH", "TRANSACTION_BACKGROUND", "MEDIUM",
                    "合同订单核验存在不一致",
                    String.join("；", contract.issues()) + "；相关材料仅作为真实性线索",
                    "NEEDS_VERIFICATION", new BigDecimal("0.75"), evidenceRefs,
                    "调阅合同、订单、发票原件并核对交易对手与资金支付对象"));
        }
    }

    private void analyzeFundFlow(LoanPurposeAnalysisData data, ToolRequest toolRequest,
                                 List<AgentFinding> findings, List<AgentDataGap> gaps,
                                 List<EvidenceRef> evidenceRefs, List<String> warnings) {
        ToolResult<LoanPurposeSnapshots.FundFlow> fundFlowResult = call(TOOL_FUND_FLOW, toolRequest);
        warnIfMock(TOOL_FUND_FLOW, fundFlowResult, warnings);
        if (!fundFlowResult.isSuccess() || fundFlowResult.getData() == null) {
            gaps.add(dataGap("LOAN_PURPOSE_FUND_FLOW", "资金流向", "资金流向分析工具未返回结果", false,
                    "UNAVAILABLE", "无法分析资金用途与回流线索", "补充银行流水或支付凭证"));
            return;
        }
        LoanPurposeSnapshots.FundFlow fundFlow = fundFlowResult.getData();
        data.setFundFlowClues(fundFlow.clues());
        data.setRoundTripSuspected(fundFlow.roundTripSuspected());
        data.setRelatedAccounts(fundFlow.relatedAccounts());
        data.setAbnormalTransferCount(fundFlow.abnormalTransferCount());
        addToolEvidenceRef(evidenceRefs, fundFlowResult, EvidenceType.CALCULATION,
                "LOAN_PURPOSE_FUND_FLOW", fundFlow.dataPeriod(), String.join("；", fundFlow.clues()));
        if (fundFlow.roundTripSuspected() || fundFlow.abnormalTransferCount() > 0
                || !fundFlow.relatedAccounts().isEmpty()) {
            findings.add(finding("LOAN_PURPOSE_FUND_FLOW_CLUE", "FUND_FLOW", "MEDIUM", "存在资金流向异常线索",
                    String.join("；", fundFlow.clues()) + "；资金回流只能作为线索，不能直接认定违规",
                    "NEEDS_VERIFICATION", new BigDecimal("0.65"), evidenceRefs,
                    "核实关联账户关系、交易路径与业务背景，必要时调阅支付凭证"));
        }
    }

    private void analyzeTerm(LoanPurposeAnalysisData data, List<AgentFinding> findings) {
        String reasonableness = termReasonableness(data.getPurposeCategory(), data.getApplyTerm(),
                data.getTermUnit());
        data.setTermReasonableness(reasonableness);
        data.setTermReasonablenessReason(termReasonMessage(reasonableness, data.getPurposeCategory(),
                data.getApplyTerm(), data.getTermUnit()));
        if ("TOO_SHORT".equals(reasonableness) || "TOO_LONG".equals(reasonableness)) {
            findings.add(finding("LOAN_PURPOSE_TERM_MISMATCH", "TERM", "LOW", "贷款期限与用途场景不匹配",
                    data.getTermReasonablenessReason(), "NEEDS_VERIFICATION", new BigDecimal("0.70"),
                    List.of(), "结合经营周期、项目回款安排与还款来源核实期限合理性"));
        }
    }

    private void analyzeRepayment(AgentContext context, LoanPurposeAnalysisData data, List<AgentFinding> findings,
                                  List<AgentDataGap> gaps, List<AgentSupplementRequest> supplementRequests,
                                  List<String> missingItems) {
        data.setPrimaryRepaymentSource("经营现金流、销售回款");
        FinancialMetrics metrics = latestFinancialMetrics(context);
        String assessment;
        if (metrics.operatingCashFlow() == null && metrics.netProfit() == null) {
            assessment = "UNKNOWN";
            gaps.add(dataGap("FINANCIAL_INDICATORS", "企业财务指标", "未获取到上游财务分析结果，无法评价第一还款来源",
                    true, "NOT_PROVIDED", "第一还款来源评价缺失", "待财务分析 Agent 完成后增量重跑"));
            supplementRequests.add(supplement("FINANCIAL_INDICATORS", "企业财务指标",
                    "贷款用途分析缺少财务数据", "补充最近一期财务报表或等待财务分析完成"));
            missingItems.add("未获取财务指标，第一还款来源评价为 UNKNOWN");
        } else if (metrics.operatingCashFlow() != null && metrics.operatingCashFlow().signum() > 0
                && metrics.netProfit() != null && metrics.netProfit().signum() > 0) {
            assessment = "STRONG";
        } else if (metrics.operatingCashFlow() != null && metrics.operatingCashFlow().signum() < 0) {
            assessment = "WEAK";
        } else {
            assessment = "MODERATE";
        }
        data.setPrimaryRepaymentAssessment(assessment);
        if ("WEAK".equals(assessment) || "UNKNOWN".equals(assessment)) {
            findings.add(finding("LOAN_PURPOSE_WEAK_PRIMARY_REPAYMENT", "REPAYMENT", "HIGH",
                    "第一还款来源评价不足",
                    "经营现金流或盈利指标不足以支撑第一还款来源，评价结果 " + assessment,
                    "NEEDS_VERIFICATION", new BigDecimal("0.75"), List.of(),
                    "核实销售回款、经营现金流与还款安排，第二还款来源不能替代第一还款来源"));
        }

        String guaranteeType = context.getCreditApplication() == null
                ? null : context.getCreditApplication().getGuaranteeType();
        data.setSecondaryRepaymentSource(secondaryRepaymentSource(guaranteeType));
        data.setSecondaryRepaymentAssessment(secondaryRepaymentAssessment(guaranteeType));
    }

    private BigDecimal amountReasonablenessRatio(BigDecimal applyAmount, BigDecimal demand) {
        return DdDeterministicMockData.divide(applyAmount, demand, 4);
    }

    private String amountReasonableness(BigDecimal applyAmount, BigDecimal demand) {
        if (applyAmount == null || demand == null || demand.signum() <= 0) {
            return "UNKNOWN";
        }
        BigDecimal ratio = amountReasonablenessRatio(applyAmount, demand);
        if (ratio.compareTo(EXCESSIVE_THRESHOLD) > 0) {
            return "EXCESSIVE";
        }
        if (ratio.compareTo(BigDecimal.ONE) > 0) {
            return "SLIGHTLY_HIGH";
        }
        if (ratio.compareTo(INSUFFICIENT_THRESHOLD) < 0) {
            return "INSUFFICIENT";
        }
        return "RATIONAL";
    }

    private String amountReasonablenessReason(String reasonableness, BigDecimal applyAmount, BigDecimal demand,
                                              BigDecimal fundingGap) {
        if ("UNKNOWN".equals(reasonableness)) {
            return "缺少申请金额或测算需求，无法判断金额合理性";
        }
        String base = "申请金额 " + plain(applyAmount) + " 元，测算需求 " + plain(demand) + " 元，资金缺口 "
                + plain(fundingGap) + " 元";
        return switch (reasonableness) {
            case "EXCESSIVE" -> base + "；申请金额超过测算需求 20%，需重点核实资金用途与支付对象";
            case "SLIGHTLY_HIGH" -> base + "；申请金额高于测算需求，需说明超出部分用途";
            case "INSUFFICIENT" -> base + "；申请金额显著低于测算需求，需核实资金缺口安排";
            default -> base + "；申请金额与测算需求基本匹配";
        };
    }

    private String termReasonableness(String category, Integer term, String unit) {
        if (term == null || term <= 0 || unit == null || !"MONTH".equalsIgnoreCase(unit)) {
            return "UNKNOWN";
        }
        if (isWorkingCapital(category)) {
            return term < 3 ? "TOO_SHORT" : term > 12 ? "TOO_LONG" : "RATIONAL";
        }
        if ("PROJECT_CONSTRUCTION".equals(category) || "EQUIPMENT_PURCHASE".equals(category)) {
            return term < 12 ? "TOO_SHORT" : term > 60 ? "TOO_LONG" : "RATIONAL";
        }
        return "UNKNOWN";
    }

    private String termReasonMessage(String reasonableness, String category, Integer term, String unit) {
        return switch (reasonableness) {
            case "RATIONAL" -> "期限 " + term + " 个月与用途场景匹配";
            case "TOO_SHORT" -> "期限 " + term + " 个月短于该用途场景的常见区间";
            case "TOO_LONG" -> "期限 " + term + " 个月长于该用途场景的常见区间";
            default -> "期限或单位缺失（" + term + " " + nullToDash(unit) + "），无法判断期限合理性";
        };
    }

    private boolean isWorkingCapital(String category) {
        return "RAW_MATERIAL_PURCHASE".equals(category) || "WORKING_CAPITAL".equals(category)
                || "TRADE_FINANCE".equals(category);
    }

    private String demandTool(String category) {
        if (isWorkingCapital(category)) {
            return TOOL_WORKING_CAPITAL;
        }
        if ("PROJECT_CONSTRUCTION".equals(category) || "EQUIPMENT_PURCHASE".equals(category)) {
            return TOOL_PROJECT;
        }
        return null;
    }

    private String secondaryRepaymentSource(String guaranteeType) {
        if (guaranteeType == null) {
            return "未提供担保方式";
        }
        return switch (guaranteeType.toUpperCase()) {
            case "MORTGAGE" -> "抵押物";
            case "PLEDGE" -> "质押物";
            case "GUARANTEE" -> "保证人";
            case "CREDIT" -> "信用（无第二还款来源）";
            default -> "担保方式：" + guaranteeType;
        };
    }

    private String secondaryRepaymentAssessment(String guaranteeType) {
        if (guaranteeType == null || "CREDIT".equalsIgnoreCase(guaranteeType)) {
            return "无有效第二还款来源，仅能依赖第一还款来源";
        }
        return "第二还款来源仅为风险缓释，需评估处置价值与变现能力，不能替代第一还款来源";
    }

    /** 读取上游财务分析的最新经营现金流与净利润，兼容对象与断点恢复后的 Map */
    private FinancialMetrics latestFinancialMetrics(AgentContext context) {
        Map<String, Object> upstream = context.getUpstreamResults();
        if (upstream == null) {
            return new FinancialMetrics(null, null);
        }
        Object data = unwrap(upstream.get(UPSTREAM_FINANCIAL_ANALYSIS));
        if (data instanceof FinancialAnalysisData analysis) {
            List<FinancialPeriodIndicator> indicators = analysis.getPeriodIndicators();
            FinancialPeriodIndicator latest = indicators == null || indicators.isEmpty()
                    ? null : indicators.getLast();
            return new FinancialMetrics(latest == null ? null : latest.getOperatingCashFlow(),
                    latest == null ? null : latest.getNetProfit());
        }
        if (data instanceof Map<?, ?> map) {
            Object periods = map.get("periodIndicators");
            if (periods instanceof List<?> list && !list.isEmpty() && list.getLast() instanceof Map<?, ?> latest) {
                return new FinancialMetrics(decimal(latest, "operatingCashFlow"), decimal(latest, "netProfit"));
            }
            return new FinancialMetrics(decimal(map, "operatingCashFlow"), decimal(map, "netProfit"));
        }
        return new FinancialMetrics(null, null);
    }

    private Object unwrap(Object value) {
        if (value instanceof AgentResult<?> result) {
            return result.getData();
        }
        if (value instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return value;
    }

    private BigDecimal decimal(Map<?, ?> data, String key) {
        Object value = data.get(key);
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

    private Map<String, Object> toolParams(AgentContext context, String creditCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("creditCode", creditCode);
        if (context.getEnterpriseId() != null) {
            params.put("enterpriseId", context.getEnterpriseId());
        }
        LoanApplicationVO application = context.getCreditApplication();
        if (application != null) {
            putIfPresent(params, "productCode", application.getProductCode());
            putIfPresent(params, "productName", application.getProductName());
            putIfPresent(params, "loanPurpose", application.getLoanPurpose());
            putIfPresent(params, "applyAmount", application.getApplyAmount());
            putIfPresent(params, "applyTerm", application.getApplyTerm());
            putIfPresent(params, "termUnit", application.getTermUnit());
            putIfPresent(params, "guaranteeType", application.getGuaranteeType());
        }
        if (context.getEnterpriseProfile() != null) {
            putIfPresent(params, "businessScope", context.getEnterpriseProfile().getBusinessScope());
            putIfPresent(params, "industryName", context.getEnterpriseProfile().getIndustryName());
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

    private void warnIfMock(String toolCode, ToolResult<?> toolResult, List<String> warnings) {
        Object mock = toolResult.getMetadata() == null ? null : toolResult.getMetadata().get("mock");
        if (Boolean.TRUE.equals(mock) || toolRegistry.require(toolCode).getDefinition().isMock()) {
            warnings.add("工具【" + toolCode + "】返回 Mock 数据（来源 " + toolResult.getSource()
                    + "），结论仅用于链路验证");
        }
    }

    private void addToolEvidenceRef(List<EvidenceRef> evidenceRefs, ToolResult<?> toolResult,
                                    EvidenceType evidenceType, String sourceReference, String dataPeriod,
                                    String excerpt) {
        List<String> evidenceIds = toolResult.getEvidenceIds();
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return;
        }
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(evidenceIds.getFirst());
        ref.setEvidenceType(evidenceType.name());
        ref.setSourceSystem(toolResult.getSource());
        ref.setSourceReference(sourceReference);
        ref.setDataPeriod(dataPeriod);
        ref.setExcerpt(excerpt);
        evidenceRefs.add(ref);
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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private List<String> investigationSuggestions(List<AgentFinding> findings) {
        return findings.stream().map(AgentFinding::getVerificationSuggestion)
                .filter(suggestion -> suggestion != null && !suggestion.isBlank()).distinct().toList();
    }

    private AgentResult<LoanPurposeAnalysisData> result(AgentContext context, LoanPurposeAnalysisData data,
                                                        List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                        List<AgentSupplementRequest> supplementRequests,
                                                        List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                        BigDecimal confidence, BigDecimal completeness, String summary) {
        AgentResult<LoanPurposeAnalysisData> result = new AgentResult<>();
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
        definition.setAgentName("贷款用途合理性分析 Agent");
        definition.setDescription("识别贷款用途分类，按场景测算资金需求，核验交易背景、资金流向与两类还款来源");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of("FINANCIAL_ANALYSIS", "INDUSTRY_EXPERT"));
        definition.setRequiredTools(List.of(TOOL_CLASSIFY, TOOL_WORKING_CAPITAL, TOOL_PROJECT,
                TOOL_CONTRACT, TOOL_FUND_FLOW));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    /** 上游财务指标：最新期经营现金流与净利润 */
    private record FinancialMetrics(BigDecimal operatingCashFlow, BigDecimal netProfit) {
    }
}
