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
 * 合同与材料异常核验工具（Mock）：识别重复合同哈希、交易对手异常，只输出待核实线索。
 */
@Component
@RequiredArgsConstructor
public class MockFraudContractTool implements AgentTool<FraudCheckSnapshots.ContractResult> {

    public static final String TOOL_CODE = "fraud.checkContractAnomalies";

    private static final String SOURCE_SYSTEM = "MOCK_DOCUMENT_OCR";

    private static final String RULE_VERSION = "1.0";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.68");

    private static final List<String> COUNTERPARTY_POOL = List.of(
            "江西恒达原材料供应有限公司", "华东设备制造有限公司", "深圳远洋贸易有限公司",
            "杭州联创包装材料有限公司", "苏州精工机械有限公司");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "合同材料异常核验（Mock）",
                "核验合同重复、交易对手关联与材料要素一致性，输出待核实线索", "ANALYSIS", true, 3000);
    }

    @Override
    public ToolResult<FraudCheckSnapshots.ContractResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        int contractCount = DdDeterministicMockData.intBetween(creditCode, "fraud-contract-count", 2, 6);
        int duplicateCount = DdDeterministicMockData.intBetween(creditCode, "fraud-contract-duplicate", 0, 2);
        boolean relatedCounterparty = DdDeterministicMockData.seed(creditCode,
                "fraud-contract-related-counterparty") % 5 == 0;
        List<String> counterparties = new ArrayList<>();
        for (int i = 0; i < Math.min(contractCount, 3); i++) {
            counterparties.add(COUNTERPARTY_POOL.get((int) ((DdDeterministicMockData.seed(creditCode,
                    "fraud-contract-counterparty") + i) % COUNTERPARTY_POOL.size())));
        }
        List<FraudCheckSnapshots.RuleHit> hits = new ArrayList<>();
        if (duplicateCount > 0) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_CON_001", RULE_VERSION, "MATERIAL", "HIGH",
                    "合同文件存在重复", "检测到 " + duplicateCount + " 份合同内容哈希重复",
                    "不同业务合同使用相同文本或相同扫描件，可能存在重复融资或材料拼凑",
                    "调阅合同原件与用印记录，核对合同编号、签署日期与交易背景"));
        }
        if (relatedCounterparty) {
            hits.add(new FraudCheckSnapshots.RuleHit("FRAUD_CON_002", RULE_VERSION, "TRANSACTION", "MEDIUM",
                    "合同交易对手疑似关联方", "交易对手 " + String.join("、", counterparties) + " 与借款主体存在名称或人员交叉",
                    "交易对手可能为关联方或包装主体，需核实交易真实性与定价公允性",
                    "核实交易对手股权结构、实际经营地与关联交易审批记录"));
        }
        String dataPeriod = YearMonth.now().toString();
        FraudCheckSnapshots.ContractResult snapshot = new FraudCheckSnapshots.ContractResult(List.copyOf(hits),
                contractCount, duplicateCount, List.copyOf(counterparties), dataPeriod);
        String summary = "Mock 合同材料异常核验：合同 " + contractCount + " 份，重复 " + duplicateCount
                + " 份，交易对手 " + String.join("、", counterparties) + "，命中规则 " + hits.size() + " 条";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CUSTOMER_DOCUMENT,
                EvidenceSourceType.OCR, SOURCE_SYSTEM, "FRAUD_CONTRACT", subjectId(request), dataPeriod,
                summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, snapshot, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true));
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        return MockToolSupport.enterpriseId(request.stringParam("enterpriseId"));
    }
}
