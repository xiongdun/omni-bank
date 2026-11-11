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
 * 银行流水查询工具（Mock）：返回近 12 个月经营流入/流出与期末余额，并登记银行流水证据。
 * 流入规模在报表收入基础上派生，用于校验经营流水与报表收入的匹配度。
 */
@Component
@RequiredArgsConstructor
public class BankStatementTool implements AgentTool<BankStatementSnapshot> {

    public static final String TOOL_CODE = "financial.getBankStatements";

    private static final String SOURCE_SYSTEM = "MOCK_BANK_STATEMENT_PROVIDER";

    private static final String DATA_PERIOD = "近12个月";

    private static final int MONTHS = 12;

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "银行流水查询（Mock）",
                "返回企业近 12 个月经营流入、经营流出与期末余额，并登记银行流水证据",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<BankStatementSnapshot> execute(ToolRequest request) {
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

        BigDecimal operatingInflow = DdDeterministicMockData.money(baseRevenue.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "bank-inflow-coverage",
                        new BigDecimal("0.55"), new BigDecimal("1.15"))));
        BigDecimal operatingOutflow = DdDeterministicMockData.money(operatingInflow.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "bank-outflow-ratio",
                        new BigDecimal("0.72"), new BigDecimal("0.95"))));
        BigDecimal endingBalance = DdDeterministicMockData.money(operatingInflow.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "bank-ending-balance-ratio",
                        new BigDecimal("0.05"), new BigDecimal("0.20"))));

        String summary = "【MOCK】近 12 个月银行流水：经营流入=" + plain(operatingInflow)
                + "元；经营流出=" + plain(operatingOutflow) + "元；期末余额=" + plain(endingBalance)
                + "元（模拟数据，仅用于链路验证）";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.FINANCIAL_STATEMENT,
                EvidenceSourceType.BANK_INTERFACE, SOURCE_SYSTEM, "bank-statement-" + creditCode + "-12m",
                creditCode, DATA_PERIOD, summary, new BigDecimal("0.80"));
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                DdDeterministicMockData.longParam(request, "enterpriseId"), item);

        ToolEvidenceRef evidenceRef = new ToolEvidenceRef(evidenceId, EvidenceType.FINANCIAL_STATEMENT.name(),
                SOURCE_SYSTEM, item.sourceReference(), item.dataPeriod(), summary);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("evidenceType", EvidenceType.FINANCIAL_STATEMENT.name());
        metadata.put("months", MONTHS);
        return ToolResult.success(SOURCE_SYSTEM,
                new BankStatementSnapshot(creditCode, DATA_PERIOD, MONTHS, operatingInflow, operatingOutflow,
                        endingBalance, List.of(evidenceRef)),
                List.of(String.valueOf(evidenceId)), metadata);
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
