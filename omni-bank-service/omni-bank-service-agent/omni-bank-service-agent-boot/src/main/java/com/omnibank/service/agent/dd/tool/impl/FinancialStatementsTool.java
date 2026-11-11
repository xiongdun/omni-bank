package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.support.DdDeterministicMockData;
import com.omnibank.service.agent.dd.support.DdMockEvidence;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 财务报表查询工具（Mock）：返回近 3 个年度资产负债表、利润表与现金流量表关键科目，
 * 并为每期登记一条财务报表证据。金额由企业信用代码派生，同一企业重复执行结果一致。
 */
@Component
@RequiredArgsConstructor
public class FinancialStatementsTool implements AgentTool<FinancialStatementsSnapshot> {

    public static final String TOOL_CODE = "financial.getFinancialStatements";

    private static final String SOURCE_SYSTEM = "MOCK_FINANCIAL_PROVIDER";

    /** 固定近 3 个完整会计年度，避免数据随系统日期漂移 */
    private static final List<String> PERIODS = List.of("2023", "2024", "2025");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "财务报表查询（Mock）",
                "返回企业近 3 个年度资产负债表、利润表与现金流量表关键科目，并登记财务报表证据",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<FinancialStatementsSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        if (creditCode == null || creditCode.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "creditCode 不能为空");
        }
        BigDecimal applyAmount = DdDeterministicMockData.decimalParam(request, "applyAmount");
        Long enterpriseId = DdDeterministicMockData.longParam(request, "enterpriseId");

        List<FinancialStatementsSnapshot.StatementPeriod> periods = new ArrayList<>();
        List<ToolEvidenceRef> evidences = new ArrayList<>();
        BigDecimal previousRevenue = null;
        for (String period : PERIODS) {
            BigDecimal revenue = revenue(creditCode, period, applyAmount, previousRevenue);
            FinancialStatementsSnapshot.StatementPeriod statement = buildPeriod(creditCode, period, revenue);
            periods.add(statement);
            previousRevenue = revenue;
            evidences.add(recordEvidence(request.taskId(), enterpriseId, creditCode, statement));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("evidenceType", EvidenceType.FINANCIAL_STATEMENT.name());
        metadata.put("periods", periods.size());
        return ToolResult.success(SOURCE_SYSTEM,
                new FinancialStatementsSnapshot(creditCode, periods, evidences),
                evidences.stream().map(evidence -> String.valueOf(evidence.evidenceId())).toList(), metadata);
    }

    /** 首期取基准收入，后续期间按派生增长率滚动，允许出现营收下滑场景 */
    private BigDecimal revenue(String creditCode, String period, BigDecimal applyAmount, BigDecimal previousRevenue) {
        if (previousRevenue == null) {
            BigDecimal base = DdDeterministicMockData.baseAmount(creditCode, "revenue-base", 2000, 12000);
            if (applyAmount != null && applyAmount.signum() > 0) {
                BigDecimal minimum = applyAmount.multiply(BigDecimal.valueOf(6));
                if (base.compareTo(minimum) < 0) {
                    base = minimum;
                }
            }
            return DdDeterministicMockData.money(base);
        }
        BigDecimal growth = DdDeterministicMockData.rateBetween(creditCode, "revenue-growth-" + period,
                new BigDecimal("-0.25"), new BigDecimal("0.25"));
        return DdDeterministicMockData.money(previousRevenue.multiply(BigDecimal.ONE.add(growth)));
    }

    private FinancialStatementsSnapshot.StatementPeriod buildPeriod(String creditCode, String period,
                                                                   BigDecimal revenue) {
        String salt = period + "-";
        BigDecimal totalAssets = DdDeterministicMockData.money(revenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "asset-scale",
                        new BigDecimal("0.90"), new BigDecimal("1.80"))));
        BigDecimal totalLiabilities = DdDeterministicMockData.money(totalAssets.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "debt-ratio",
                        new BigDecimal("0.35"), new BigDecimal("0.80"))));
        BigDecimal netAssets = DdDeterministicMockData.money(totalAssets.subtract(totalLiabilities));

        BigDecimal currentLiabilities = DdDeterministicMockData.money(totalLiabilities.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "current-liability-share",
                        new BigDecimal("0.55"), new BigDecimal("0.85"))));
        BigDecimal shortTermDebt = DdDeterministicMockData.money(currentLiabilities.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "short-debt-share",
                        new BigDecimal("0.25"), new BigDecimal("0.55"))));
        BigDecimal currentAssets = DdDeterministicMockData.money(currentLiabilities.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "current-ratio",
                        new BigDecimal("0.80"), new BigDecimal("2.00"))));
        BigDecimal inventory = DdDeterministicMockData.money(currentAssets.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "inventory-share",
                        new BigDecimal("0.15"), new BigDecimal("0.35"))));
        BigDecimal accountsReceivable = DdDeterministicMockData.money(currentAssets.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "receivable-share",
                        new BigDecimal("0.20"), new BigDecimal("0.40"))));
        BigDecimal cash = DdDeterministicMockData.money(currentAssets.subtract(inventory).subtract(accountsReceivable));

        BigDecimal grossMargin = DdDeterministicMockData.rateBetween(creditCode, salt + "gross-margin",
                new BigDecimal("0.08"), new BigDecimal("0.35"));
        BigDecimal costOfSales = DdDeterministicMockData.money(revenue.subtract(revenue.multiply(grossMargin)));
        BigDecimal netProfit = DdDeterministicMockData.money(revenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "net-margin",
                        new BigDecimal("0.005"), new BigDecimal("0.12"))));
        BigDecimal interestExpense = DdDeterministicMockData.money(totalLiabilities.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "interest-rate",
                        new BigDecimal("0.03"), new BigDecimal("0.06"))));
        BigDecimal ebitda = DdDeterministicMockData.money(netProfit.add(interestExpense).add(revenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "depreciation",
                        new BigDecimal("0.02"), new BigDecimal("0.05")))));
        BigDecimal operatingCashFlow = DdDeterministicMockData.money(revenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "ocf-ratio",
                        new BigDecimal("-0.08"), new BigDecimal("0.18"))));
        BigDecimal investingCashFlow = DdDeterministicMockData.money(revenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "invest-ratio",
                        new BigDecimal("-0.10"), new BigDecimal("-0.02"))));
        BigDecimal financingCashFlow = DdDeterministicMockData.money(revenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, salt + "finance-ratio",
                        new BigDecimal("-0.05"), new BigDecimal("0.08"))));

        return new FinancialStatementsSnapshot.StatementPeriod(period, totalAssets, currentAssets, cash,
                accountsReceivable, inventory, totalLiabilities, currentLiabilities, shortTermDebt, netAssets,
                revenue, costOfSales, netProfit, interestExpense, ebitda, operatingCashFlow, investingCashFlow,
                financingCashFlow);
    }

    private ToolEvidenceRef recordEvidence(Long taskId, Long enterpriseId, String creditCode,
                                           FinancialStatementsSnapshot.StatementPeriod statement) {
        String summary = "【MOCK】" + statement.period() + "年度财务报表：营业收入="
                + plain(statement.revenue()) + "元；净利润=" + plain(statement.netProfit())
                + "元；资产总额=" + plain(statement.totalAssets()) + "元；负债合计="
                + plain(statement.totalLiabilities()) + "元；经营活动现金流净额="
                + plain(statement.operatingCashFlow()) + "元（模拟数据，仅用于链路验证）";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.FINANCIAL_STATEMENT,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM,
                "financial-statement-" + creditCode + "-" + statement.period(), creditCode,
                statement.period() + "年度", summary, new BigDecimal("0.85"));
        Long evidenceId = evidenceService.recordEvidence(taskId, enterpriseId, item);
        return new ToolEvidenceRef(evidenceId, EvidenceType.FINANCIAL_STATEMENT.name(), SOURCE_SYSTEM,
                item.sourceReference(), item.dataPeriod(), summary);
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
