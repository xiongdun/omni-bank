package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 报告完整性校验工具：必备章节必须为 AVAILABLE 且有内容，要求证据的章节必须给出证据引用
 */
@Component
public class ReportValidateTool implements AgentTool<ReportSnapshots.ValidationResult> {

    public static final String TOOL_CODE = "report.validateReport";

    private static final String SOURCE = "DD_REPORT_TEMPLATE";

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "尽调报告完整性校验",
                "校验必备章节是否完成、内容是否为空以及证据引用是否缺失", "RULE", false, 1000);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult<ReportSnapshots.ValidationResult> execute(ToolRequest request) {
        Object rawChapters = request.params().get("chapters");
        if (!(rawChapters instanceof List<?> chapterList)) {
            return ToolResult.failure(TOOL_CODE, null, "chapters 不能为空");
        }
        Map<String, Map<String, Object>> builtChapters = new LinkedHashMap<>();
        for (Object item : chapterList) {
            if (item instanceof Map<?, ?> map) {
                Object code = map.get("chapterCode");
                if (code != null) {
                    builtChapters.put(String.valueOf(code), (Map<String, Object>) map);
                }
            }
        }

        List<String> issues = new ArrayList<>();
        List<String> missingChapters = new ArrayList<>();
        List<String> chaptersWithoutEvidence = new ArrayList<>();
        for (ReportSnapshots.ChapterTemplate template : ReportTemplateCatalog.chapters()) {
            Map<String, Object> built = builtChapters.get(template.chapterCode());
            if (template.required()) {
                if (built == null) {
                    missingChapters.add(template.title());
                    issues.add("缺少必备章节：" + template.title());
                } else if (!STATUS_AVAILABLE.equals(String.valueOf(built.get("status")))) {
                    issues.add("必备章节未完成：" + template.title() + "（状态 " + built.get("status") + "）");
                } else if (isBlank(built.get("content"))) {
                    issues.add("必备章节内容为空：" + template.title());
                }
            }
            if (template.requireEvidence()) {
                Object evidenceIds = built == null ? null : built.get("evidenceIds");
                if (!(evidenceIds instanceof List<?> ids) || ids.isEmpty()) {
                    chaptersWithoutEvidence.add(template.title());
                    issues.add("章节缺少证据引用：" + template.title());
                }
            }
        }
        ReportSnapshots.ValidationResult result = new ReportSnapshots.ValidationResult(issues.isEmpty(),
                List.copyOf(issues), List.copyOf(missingChapters), List.copyOf(chaptersWithoutEvidence));
        return ToolResult.success(SOURCE, result, List.of(),
                Map.of("issueCount", issues.size(), "chapterCount", builtChapters.size()));
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
