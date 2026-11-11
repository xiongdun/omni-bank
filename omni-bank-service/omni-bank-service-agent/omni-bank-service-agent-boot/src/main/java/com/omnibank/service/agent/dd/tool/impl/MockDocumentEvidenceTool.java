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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 材料证据登记工具（Mock OCR）：把归类后的材料按文件哈希登记为客户材料证据。
 * 系统尚未接入真实 OCR，来源系统固定标记 MOCK_OCR_PROVIDER，不能视为真实识别结果。
 */
@Component
@RequiredArgsConstructor
public class MockDocumentEvidenceTool implements AgentTool<Long> {

    public static final String TOOL_CODE = "document.createEvidence";

    private static final String SOURCE_SYSTEM = "MOCK_OCR_PROVIDER";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.85");

    private static final int EXCERPT_LIMIT = 160;

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "材料证据登记（Mock OCR）",
                "按文件哈希登记客户材料证据，来源标记为 MOCK_OCR_PROVIDER，不代表真实 OCR 结果", "ACTION", true, 3000);
    }

    @Override
    public ToolResult<Long> execute(ToolRequest request) {
        String docType = request.stringParam("docType");
        String docTypeName = request.stringParam("docTypeName");
        String fileName = request.stringParam("fileName");
        String fileHash = request.stringParam("fileHash");
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        if (docType == null || docType.isBlank() || fileName == null || fileName.isBlank()
                || fileHash == null || fileHash.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "docType、fileName、fileHash 不能为空");
        }

        String summary = "Mock OCR 材料：类型【" + docTypeName + "】，文件 " + fileName + "，摘要："
                + excerpt(request.stringParam("textExcerpt"));
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CUSTOMER_DOCUMENT, EvidenceSourceType.OCR,
                SOURCE_SYSTEM, fileHash, subjectId(request), request.stringParam("dataPeriod"), summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);
        return ToolResult.success(SOURCE_SYSTEM, evidenceId, List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "docType", docType));
    }

    private String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "无可读文本";
        }
        return text.length() <= EXCERPT_LIMIT ? text : text.substring(0, EXCERPT_LIMIT);
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
