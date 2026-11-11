package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 材料分类工具（规则类）：按文件名与可读文本中的关键字归类，不依赖 OCR 与外部服务。
 * 分类只说明材料可能属于哪一类，不能据此证明材料真实有效。
 */
@Component
public class MockDocumentClassifyTool implements AgentTool<DocumentClassification> {

    public static final String TOOL_CODE = "document.classifyDocument";

    private static final BigDecimal FILE_NAME_CONFIDENCE = new BigDecimal("0.95");

    private static final BigDecimal EXCERPT_CONFIDENCE = new BigDecimal("0.82");

    private static final BigDecimal UNRECOGNIZED_CONFIDENCE = new BigDecimal("0.30");

    /** 按业务优先级排列，靠前规则先匹配 */
    private static final List<ClassifyRule> RULES = List.of(
            new ClassifyRule(MaterialDocTypes.BUSINESS_LICENSE, "营业执照", List.of("营业执照")),
            new ClassifyRule(MaterialDocTypes.ARTICLES_OF_ASSOCIATION, "公司章程", List.of("公司章程", "章程")),
            new ClassifyRule(MaterialDocTypes.FINANCIAL_STATEMENT, "财务报表",
                    List.of("财务报表", "审计报告", "资产负债表", "利润表", "现金流量表")),
            new ClassifyRule(MaterialDocTypes.BANK_STATEMENT, "银行流水", List.of("银行流水", "对账单", "交易明细")),
            new ClassifyRule(MaterialDocTypes.TAX_CERTIFICATE, "纳税证明",
                    List.of("纳税证明", "完税证明", "纳税申报", "纳税信用")),
            new ClassifyRule(MaterialDocTypes.CREDIT_AUTHORIZATION, "征信授权书",
                    List.of("征信授权", "征信查询授权")),
            new ClassifyRule(MaterialDocTypes.GUARANTEE_CONTRACT, "担保合同",
                    List.of("担保合同", "保证合同", "抵押合同", "质押合同")),
            new ClassifyRule(MaterialDocTypes.SALES_CONTRACT, "购销合同",
                    List.of("购销合同", "采购合同", "销售合同", "订单")),
            new ClassifyRule(MaterialDocTypes.PROPERTY_CERTIFICATE, "权属证明",
                    List.of("不动产权证", "房产证", "他项权证", "权属证明")));

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "材料分类（规则）",
                "按文件名与可读文本关键字归类客户材料，输出类别、置信度与命中依据", "RULE", true, 1000);
    }

    @Override
    public ToolResult<DocumentClassification> execute(ToolRequest request) {
        String fileName = request.stringParam("fileName");
        String excerpt = request.stringParam("textExcerpt");
        if ((fileName == null || fileName.isBlank()) && (excerpt == null || excerpt.isBlank())) {
            return ToolResult.failure(TOOL_CODE, null, "fileName 与 textExcerpt 不能同时为空");
        }
        return ToolResult.success(TOOL_CODE, classify(fileName, excerpt), List.of(),
                Map.of("mock", true, "ruleEngine", true));
    }

    private DocumentClassification classify(String fileName, String excerpt) {
        for (ClassifyRule rule : RULES) {
            for (String keyword : rule.keywords()) {
                if (fileName != null && fileName.contains(keyword)) {
                    return new DocumentClassification(rule.docType(), rule.docTypeName(), FILE_NAME_CONFIDENCE,
                            "文件名命中关键字【" + keyword + "】");
                }
            }
        }
        for (ClassifyRule rule : RULES) {
            for (String keyword : rule.keywords()) {
                if (excerpt != null && excerpt.contains(keyword)) {
                    return new DocumentClassification(rule.docType(), rule.docTypeName(), EXCERPT_CONFIDENCE,
                            "可读文本命中关键字【" + keyword + "】");
                }
            }
        }
        return new DocumentClassification(MaterialDocTypes.UNRECOGNIZED, "无法归类", UNRECOGNIZED_CONFIDENCE,
                "未命中任何材料关键字");
    }

    private record ClassifyRule(String docType, String docTypeName, List<String> keywords) {
    }
}
