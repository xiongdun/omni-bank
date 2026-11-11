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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 合同、订单与发票核验工具（Mock）：返回交易背景要素与一致性问题，真实性判断留给人工。
 */
@Component
@RequiredArgsConstructor
public class MockLoanPurposeContractTool implements AgentTool<LoanPurposeSnapshots.Contract> {

    public static final String TOOL_CODE = "loanPurpose.analyzeContract";

    private static final String SOURCE_SYSTEM = "MOCK_CONTRACT_REPOSITORY";

    private static final List<String> COUNTERPARTY_POOL = List.of(
            "江西恒达原材料供应有限公司", "华东设备制造有限公司", "深圳远洋贸易有限公司",
            "杭州联创包装材料有限公司", "苏州精工机械有限公司", "广州顺达物流有限公司");

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.72");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "合同订单核验（Mock）",
                "核验采购/销售合同、订单与发票的数量、金额、交易对手与用途一致性", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<LoanPurposeSnapshots.Contract> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        int contractCount = DdDeterministicMockData.intBetween(creditCode, "loan-purpose-contract-count", 2, 6);
        int orderCount = DdDeterministicMockData.intBetween(creditCode, "loan-purpose-order-count", 1, 4);
        int invoiceCount = DdDeterministicMockData.intBetween(creditCode, "loan-purpose-invoice-count", 1, 8);
        BigDecimal totalContractAmount = DdDeterministicMockData.baseAmount(creditCode,
                "loan-purpose-contract-amount", 300, 1500);
        long seed = DdDeterministicMockData.seed(creditCode, "loan-purpose-contract-consistency");
        boolean purposeConsistent = seed % 5 != 0;
        boolean invoicesMatched = seed % 4 != 0;
        List<String> counterparties = new ArrayList<>();
        for (int i = 0; i < Math.min(contractCount, 3); i++) {
            counterparties.add(COUNTERPARTY_POOL.get((int) ((seed + i) % COUNTERPARTY_POOL.size())));
        }
        List<String> issues = new ArrayList<>();
        if (!purposeConsistent) {
            issues.add("合同标的与申请用途不一致，需核实实际交易背景");
        }
        if (!invoicesMatched) {
            issues.add("发票金额与合同金额匹配度不足，需核实开票与结算记录");
        }
        String dataPeriod = YearMonth.now().toString();
        LoanPurposeSnapshots.Contract snapshot = new LoanPurposeSnapshots.Contract(contractCount, orderCount,
                invoiceCount, totalContractAmount, List.copyOf(counterparties), purposeConsistent, invoicesMatched,
                List.copyOf(issues), dataPeriod);
        String summary = "Mock 合同订单核验：合同 " + contractCount + " 份、订单 " + orderCount + " 笔、发票 "
                + invoiceCount + " 张，合同金额 " + totalContractAmount.toPlainString() + " 元，交易对手 "
                + String.join("、", counterparties) + "；用途一致=" + purposeConsistent + "，发票匹配=" + invoicesMatched;
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CUSTOMER_DOCUMENT,
                EvidenceSourceType.CUSTOMER_UPLOAD, SOURCE_SYSTEM, "LOAN_PURPOSE_CONTRACT", subjectId(request),
                dataPeriod, summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true));
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        String value = request.stringParam("enterpriseId");
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }
}
