package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.support.DdMockEvidence;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 贷款用途分类工具（Mock）：按贷款用途与产品编码关键词匹配用途分类，不调用大模型。
 */
@Component
@RequiredArgsConstructor
public class MockLoanPurposeClassifyTool implements AgentTool<LoanPurposeSnapshots.Classification> {

    public static final String TOOL_CODE = "loanPurpose.classifyLoanPurpose";

    private static final String SOURCE_SYSTEM = "MOCK_LOAN_PURPOSE_ENGINE";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.80");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "贷款用途分类（Mock）",
                "按贷款用途与产品编码关键词识别原材料采购、经营周转、设备购置、项目建设、贸易融资等分类",
                "RULE", true, 3000);
    }

    @Override
    public ToolResult<LoanPurposeSnapshots.Classification> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String purpose = request.stringParam("loanPurpose");
        String productCode = request.stringParam("productCode");
        LoanPurposeSnapshots.Classification classification = classify(purpose, productCode);
        String summary = "Mock 贷款用途分类：" + classification.categoryName() + "（"
                + classification.purposeCategory() + "），业务场景 " + classification.businessScenario()
                + "，匹配关键词 " + classification.matchedKeyword();
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CALCULATION,
                EvidenceSourceType.FINANCIAL_CALCULATION, SOURCE_SYSTEM,
                "LOAN_PURPOSE_CLASS:" + classification.purposeCategory(), subjectId(request),
                classification.dataPeriod(), summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, classification, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true));
    }

    private LoanPurposeSnapshots.Classification classify(String purpose, String productCode) {
        String text = purpose == null ? "" : purpose.trim();
        String product = productCode == null ? "" : productCode.trim().toUpperCase();
        String dataPeriod = YearMonth.now().toString();
        if (containsAny(text, "原材料", "采购")) {
            return new LoanPurposeSnapshots.Classification("RAW_MATERIAL_PURCHASE", "原材料采购", "流动资金贷款",
                    matched(text, "原材料", "采购"), dataPeriod);
        }
        if (containsAny(text, "周转", "经营")) {
            return new LoanPurposeSnapshots.Classification("WORKING_CAPITAL", "日常经营周转", "流动资金贷款",
                    matched(text, "周转", "经营"), dataPeriod);
        }
        if (containsAny(text, "设备", "购置")) {
            return new LoanPurposeSnapshots.Classification("EQUIPMENT_PURCHASE", "设备购置", "固定资产贷款",
                    matched(text, "设备", "购置"), dataPeriod);
        }
        if (containsAny(text, "建设", "项目", "工程")) {
            return new LoanPurposeSnapshots.Classification("PROJECT_CONSTRUCTION", "项目建设", "项目融资",
                    matched(text, "建设", "项目", "工程"), dataPeriod);
        }
        if (containsAny(text, "贸易", "信用证", "进口", "出口")) {
            return new LoanPurposeSnapshots.Classification("TRADE_FINANCE", "贸易融资", "贸易融资",
                    matched(text, "贸易", "信用证", "进口", "出口"), dataPeriod);
        }
        if (product.contains("FIXED_ASSET")) {
            return new LoanPurposeSnapshots.Classification("EQUIPMENT_PURCHASE", "设备购置", "固定资产贷款",
                    "产品编码", dataPeriod);
        }
        if (product.contains("TRADE")) {
            return new LoanPurposeSnapshots.Classification("TRADE_FINANCE", "贸易融资", "贸易融资",
                    "产品编码", dataPeriod);
        }
        if (product.contains("WORKING_CAPITAL")) {
            return new LoanPurposeSnapshots.Classification("WORKING_CAPITAL", "日常经营周转", "流动资金贷款",
                    "产品编码", dataPeriod);
        }
        return new LoanPurposeSnapshots.Classification("OTHER", "其他用途", "待确认", "-", dataPeriod);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String matched(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return "-";
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
