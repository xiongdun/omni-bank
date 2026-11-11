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
 * 债务到期查询工具（Mock）：返回短/中/长期有息负债与未来 6/12 个月到期金额，并登记征信类证据。
 * 债务规模以征信已用授信为基准派生，保证与征信报告口径一致。
 */
@Component
@RequiredArgsConstructor
public class DebtMaturityTool implements AgentTool<DebtMaturitySnapshot> {

    public static final String TOOL_CODE = "credit.getDebtMaturity";

    private static final String SOURCE_SYSTEM = "MOCK_DEBT_PROVIDER";

    private static final String DATA_PERIOD = "未来12个月";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "债务期限结构查询（Mock）",
                "返回企业短/中/长期有息负债与未来 6/12 个月到期金额，并登记债务到期证据",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<DebtMaturitySnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        if (creditCode == null || creditCode.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "creditCode 不能为空");
        }
        BigDecimal usedCredit = DdDeterministicMockData.decimalParam(request, "usedCredit");
        BigDecimal applyAmount = DdDeterministicMockData.decimalParam(request, "applyAmount");

        BigDecimal totalDebt = usedCredit != null && usedCredit.signum() > 0
                ? DdDeterministicMockData.money(usedCredit.multiply(
                        DdDeterministicMockData.rateBetween(creditCode, "debt-scale",
                                new BigDecimal("1.00"), new BigDecimal("1.40"))))
                : DdDeterministicMockData.baseAmount(creditCode, "debt-base", 500, 5000);
        if (applyAmount != null && applyAmount.signum() > 0) {
            BigDecimal minimum = applyAmount.multiply(BigDecimal.valueOf(2));
            if (totalDebt.compareTo(minimum) < 0) {
                totalDebt = minimum.setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal shortShare = DdDeterministicMockData.rateBetween(creditCode, "short-debt-share",
                new BigDecimal("0.35"), new BigDecimal("0.70"));
        BigDecimal midShare = DdDeterministicMockData.rateBetween(creditCode, "mid-debt-share",
                new BigDecimal("0.15"), new BigDecimal("0.35"));
        BigDecimal midCap = BigDecimal.ONE.subtract(shortShare).multiply(new BigDecimal("0.60"));
        if (midShare.compareTo(midCap) > 0) {
            midShare = midCap;
        }
        BigDecimal shortTermDebt = DdDeterministicMockData.money(totalDebt.multiply(shortShare));
        BigDecimal midTermDebt = DdDeterministicMockData.money(totalDebt.multiply(midShare));
        BigDecimal longTermDebt = DdDeterministicMockData.money(
                totalDebt.subtract(shortTermDebt).subtract(midTermDebt));

        BigDecimal dueWithin6Months = DdDeterministicMockData.money(shortTermDebt.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "due-6m-share",
                        new BigDecimal("0.45"), new BigDecimal("1.00"))));
        BigDecimal dueWithin12Months = DdDeterministicMockData.money(dueWithin6Months.add(midTermDebt.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "due-12m-share",
                        new BigDecimal("0.10"), new BigDecimal("0.50")))));
        if (dueWithin12Months.compareTo(totalDebt) > 0) {
            dueWithin12Months = DdDeterministicMockData.money(totalDebt);
        }

        String summary = "【MOCK】债务期限结构：有息负债合计=" + plain(totalDebt) + "元；短期="
                + plain(shortTermDebt) + "元；中期=" + plain(midTermDebt) + "元；长期=" + plain(longTermDebt)
                + "元；未来 6 个月到期=" + plain(dueWithin6Months) + "元；未来 12 个月到期="
                + plain(dueWithin12Months) + "元（模拟数据，仅用于链路验证）";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CREDIT_REPORT,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM, "debt-maturity-" + creditCode, creditCode,
                DATA_PERIOD, summary, new BigDecimal("0.83"));
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                DdDeterministicMockData.longParam(request, "enterpriseId"), item);

        ToolEvidenceRef evidenceRef = new ToolEvidenceRef(evidenceId, EvidenceType.CREDIT_REPORT.name(),
                SOURCE_SYSTEM, item.sourceReference(), item.dataPeriod(), summary);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("evidenceType", EvidenceType.CREDIT_REPORT.name());
        return ToolResult.success(SOURCE_SYSTEM,
                new DebtMaturitySnapshot(creditCode, shortTermDebt, midTermDebt, longTermDebt, totalDebt,
                        dueWithin6Months, dueWithin12Months, List.of(evidenceRef)),
                List.of(String.valueOf(evidenceId)), metadata);
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
