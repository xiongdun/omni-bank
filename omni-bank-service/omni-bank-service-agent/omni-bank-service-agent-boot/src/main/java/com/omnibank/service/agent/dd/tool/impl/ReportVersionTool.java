package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 报告版本工具：新增一个报告版本并持久化章节内容，版本号在任务内递增
 */
@Component
@RequiredArgsConstructor
public class ReportVersionTool implements AgentTool<ReportSnapshots.VersionResult> {

    public static final String TOOL_CODE = "report.createReportVersion";

    private static final String SOURCE = "DD_REPORT";

    private final DdReportService reportService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "尽调报告版本落库",
                "按任务生成递增版本，持久化报告状态、章节内容、证据引用与缺口统计", "ACTION", false, 3000);
    }

    @Override
    public ToolResult<ReportSnapshots.VersionResult> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        Map<String, Object> params = request.params();
        Object chapters = params.get("chapters");
        if (!(chapters instanceof List<?>)) {
            return ToolResult.failure(TOOL_CODE, null, "chapters 不能为空");
        }
        DdDueDiligenceReport report = reportService.createVersion(request.taskId(),
                stringParam(params, "title"),
                stringParam(params, "reportStatus"),
                stringParam(params, "reviewStatus"),
                chapters,
                stringList(params.get("evidenceIds")),
                intParam(params, "evidenceCount"),
                intParam(params, "dataGapCount"),
                intParam(params, "supplementRequestCount"),
                stringParam(params, "exportStatus"),
                stringParam(params, "modelVersion"),
                stringParam(params, "promptVersion"));
        ReportSnapshots.VersionResult result = new ReportSnapshots.VersionResult(report.getId(),
                report.getReportNo(), report.getReportVersion(), report.getReportStatus(),
                report.getGeneratedAt());
        return ToolResult.success(SOURCE, result, List.of(),
                Map.of("reportId", report.getId(), "reportVersion", report.getReportVersion()));
    }

    private String stringParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private int intParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }
}
