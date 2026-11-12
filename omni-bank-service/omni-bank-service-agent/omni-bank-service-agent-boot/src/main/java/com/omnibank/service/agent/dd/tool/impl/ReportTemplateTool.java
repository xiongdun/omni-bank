package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 报告模板工具：返回固定章节模板与必备章节标记，供报告 Agent 与校验工具共用
 */
@Component
public class ReportTemplateTool implements AgentTool<ReportSnapshots.TemplateResult> {

    public static final String TOOL_CODE = "report.getTemplate";

    private static final String SOURCE = "DD_REPORT_TEMPLATE";

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "尽调报告模板",
                "返回固定 18 章报告模板，标注必备章节、证据要求与数据来源 Agent", "RULE", false, 1000);
    }

    @Override
    public ToolResult<ReportSnapshots.TemplateResult> execute(ToolRequest request) {
        List<ReportSnapshots.ChapterTemplate> chapters = ReportTemplateCatalog.chapters();
        ReportSnapshots.TemplateResult result =
                new ReportSnapshots.TemplateResult(ReportTemplateCatalog.TEMPLATE_VERSION, chapters);
        return ToolResult.success(SOURCE, result, List.of(),
                Map.of("templateVersion", ReportTemplateCatalog.TEMPLATE_VERSION, "chapterCount", chapters.size()));
    }
}
