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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 纳税申报数据查询工具（Mock）：返回增值税/所得税申报收入与实缴税额，并登记财务报表类证据。
 * 申报收入在报表收入基础上派生稳定偏离，用于支持税务与报表勾稽分析。
 */
@Component
@RequiredArgsConstructor
public class TaxDataTool implements AgentTool<TaxDataSnapshot> {

    public static final String TOOL_CODE = "financial.getTaxData";

    private static final String SOURCE_SYSTEM = "MOCK_TAX_PROVIDER";

    private static final String DATA_PERIOD = "2025年度";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "纳税申报数据查询（Mock）",
                "返回增值税/所得税申报收入与实缴税额，并登记纳税申报证据", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<TaxDataSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        if (creditCode == null || creditCode.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "creditCode 不能为空");
        }
        BigDecimal latestRevenue = DdDeterministicMockData.decimalParam(request, "latestRevenue");
        BigDecimal baseRevenue = latestRevenue != null && latestRevenue.signum() > 0
                ? latestRevenue : DdDeterministicMockData.baseAmount(creditCode, "revenue-base", 2000, 12000);

        BigDecimal deviation = DdDeterministicMockData.rateBetween(creditCode, "tax-deviation",
                new BigDecimal("-0.25"), new BigDecimal("0.25"));
        BigDecimal vatRevenue = DdDeterministicMockData.money(baseRevenue.multiply(BigDecimal.ONE.add(deviation)));
        BigDecimal incomeTaxRevenue = DdDeterministicMockData.money(vatRevenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "income-tax-revenue-ratio",
                        new BigDecimal("0.90"), new BigDecimal("1.05"))));
        BigDecimal vatPaidTax = DdDeterministicMockData.money(vatRevenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "vat-pay-ratio",
                        new BigDecimal("0.02"), new BigDecimal("0.05"))));
        BigDecimal incomeTaxPaidTax = DdDeterministicMockData.money(incomeTaxRevenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "income-tax-pay-ratio",
                        new BigDecimal("0.01"), new BigDecimal("0.06"))));

        String summary = "【MOCK】纳税申报数据：增值税申报收入=" + plain(vatRevenue)
                + "元；所得税申报收入=" + plain(incomeTaxRevenue) + "元；增值税实缴=" + plain(vatPaidTax)
                + "元；所得税实缴=" + plain(incomeTaxPaidTax) + "元（模拟数据，仅用于链路验证）";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.FINANCIAL_STATEMENT,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM,
                "tax-data-" + creditCode + "-" + DATA_PERIOD, creditCode, DATA_PERIOD, summary,
                new BigDecimal("0.82"));
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                DdDeterministicMockData.longParam(request, "enterpriseId"), item);

        ToolEvidenceRef evidenceRef = new ToolEvidenceRef(evidenceId, EvidenceType.FINANCIAL_STATEMENT.name(),
                SOURCE_SYSTEM, item.sourceReference(), item.dataPeriod(), summary);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("evidenceType", EvidenceType.FINANCIAL_STATEMENT.name());
        return ToolResult.success(SOURCE_SYSTEM,
                new TaxDataSnapshot(creditCode, DATA_PERIOD, vatRevenue, vatRevenue, incomeTaxRevenue,
                        vatPaidTax, incomeTaxPaidTax, List.of(evidenceRef)),
                List.of(String.valueOf(evidenceId)), metadata);
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
