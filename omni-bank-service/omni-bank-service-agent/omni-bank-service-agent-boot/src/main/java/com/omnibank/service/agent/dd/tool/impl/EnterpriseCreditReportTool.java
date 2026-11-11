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
 * 企业征信报告查询工具（Mock）：返回授信总额/已用/可用、逾期次数与金额、对外担保余额及近 12 个月还款记录，
 * 并登记征信报告证据。数据由企业信用代码派生，同一企业重复执行结果一致。
 */
@Component
@RequiredArgsConstructor
public class EnterpriseCreditReportTool implements AgentTool<EnterpriseCreditReportSnapshot> {

    public static final String TOOL_CODE = "credit.getEnterpriseCreditReport";

    private static final String SOURCE_SYSTEM = "MOCK_CREDIT_PROVIDER";

    private static final String DATA_PERIOD = "2025年度";

    private static final int REPAYMENT_MONTHS = 12;

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "企业征信报告查询（Mock）",
                "返回企业授信使用、逾期、对外担保与近 12 个月还款记录，并登记征信报告证据",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<EnterpriseCreditReportSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        if (creditCode == null || creditCode.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "creditCode 不能为空");
        }
        BigDecimal applyAmount = DdDeterministicMockData.decimalParam(request, "applyAmount");
        BigDecimal netAssets = DdDeterministicMockData.decimalParam(request, "netAssets");

        BigDecimal creditLimit = DdDeterministicMockData.baseAmount(creditCode, "credit-limit", 2000, 15000);
        if (applyAmount != null && applyAmount.signum() > 0) {
            BigDecimal minimum = applyAmount.multiply(BigDecimal.valueOf(3));
            if (creditLimit.compareTo(minimum) < 0) {
                creditLimit = minimum.setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal usedCredit = DdDeterministicMockData.money(creditLimit.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "credit-utilization",
                        new BigDecimal("0.25"), new BigDecimal("0.92"))));
        BigDecimal availableCredit = DdDeterministicMockData.money(creditLimit.subtract(usedCredit));

        int overdueCount = DdDeterministicMockData.intBetween(creditCode, "overdue-count", 0, 2);
        BigDecimal overdueAmount = overdueCount == 0 ? new BigDecimal("0.00")
                : DdDeterministicMockData.money(usedCredit.multiply(
                        DdDeterministicMockData.rateBetween(creditCode, "overdue-amount-ratio",
                                new BigDecimal("0.01"), new BigDecimal("0.08"))));
        BigDecimal reportedNetAssets = netAssets != null && netAssets.signum() > 0
                ? netAssets
                : DdDeterministicMockData.money(creditLimit.multiply(
                        DdDeterministicMockData.rateBetween(creditCode, "report-net-assets-ratio",
                                new BigDecimal("1.50"), new BigDecimal("4.00"))));
        BigDecimal externalGuaranteeBalance = DdDeterministicMockData.money(reportedNetAssets.multiply(
                DdDeterministicMockData.rateBetween(creditCode, "guarantee-ratio",
                        new BigDecimal("0.05"), new BigDecimal("0.70"))));

        List<EnterpriseCreditReportSnapshot.RepaymentRecord> repaymentRecords =
                repaymentRecords(creditCode, usedCredit, overdueCount);
        int overdueMonths = (int) repaymentRecords.stream()
                .filter(record -> "OVERDUE".equals(record.status())).count();

        String summary = "【MOCK】企业征信摘要：授信总额=" + plain(creditLimit) + "元；已用授信="
                + plain(usedCredit) + "元；可用授信=" + plain(availableCredit) + "元；逾期次数=" + overdueCount
                + "；逾期金额=" + plain(overdueAmount) + "元；对外担保余额=" + plain(externalGuaranteeBalance)
                + "元；报告口径净资产=" + plain(reportedNetAssets)
                + "元；近 12 个月还款记录中逾期 " + overdueMonths + " 期（模拟数据，仅用于链路验证）";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CREDIT_REPORT,
                EvidenceSourceType.EXTERNAL_DATA, SOURCE_SYSTEM, "credit-report-" + creditCode, creditCode,
                DATA_PERIOD, summary, new BigDecimal("0.85"));
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                DdDeterministicMockData.longParam(request, "enterpriseId"), item);

        ToolEvidenceRef evidenceRef = new ToolEvidenceRef(evidenceId, EvidenceType.CREDIT_REPORT.name(),
                SOURCE_SYSTEM, item.sourceReference(), item.dataPeriod(), summary);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("evidenceType", EvidenceType.CREDIT_REPORT.name());
        metadata.put("repaymentMonths", REPAYMENT_MONTHS);
        return ToolResult.success(SOURCE_SYSTEM,
                new EnterpriseCreditReportSnapshot(creditCode, creditLimit, usedCredit, availableCredit,
                        overdueCount, overdueAmount, externalGuaranteeBalance, reportedNetAssets,
                        repaymentRecords, List.of(evidenceRef)),
                List.of(String.valueOf(evidenceId)), metadata);
    }

    /** 近 12 个月还款记录：逾期月份从派生起点连续排布，逾期期数与逾期次数保持一致 */
    private List<EnterpriseCreditReportSnapshot.RepaymentRecord> repaymentRecords(String creditCode,
                                                                                  BigDecimal usedCredit,
                                                                                  int overdueCount) {
        List<EnterpriseCreditReportSnapshot.RepaymentRecord> records = new ArrayList<>();
        int overdueStart = DdDeterministicMockData.intBetween(creditCode, "overdue-start-month", 0, 11);
        for (int index = 0; index < REPAYMENT_MONTHS; index++) {
            boolean overdue = overdueCount > 0 && (index - overdueStart + REPAYMENT_MONTHS) % REPAYMENT_MONTHS
                    < overdueCount;
            BigDecimal amount = DdDeterministicMockData.money(usedCredit.multiply(
                    DdDeterministicMockData.rateBetween(creditCode, "repayment-amount-" + index,
                            new BigDecimal("0.02"), new BigDecimal("0.08"))));
            int month = index + 1;
            records.add(new EnterpriseCreditReportSnapshot.RepaymentRecord(
                    "2025-" + (month < 10 ? "0" : "") + month, amount, overdue ? "OVERDUE" : "NORMAL"));
        }
        return records;
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
