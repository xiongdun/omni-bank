package com.omnibank.service.agent.dd.tool;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Tool 注册表：负责工具发现与统一查找，重复注册在启动期直接失败
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool<?>> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool<?>> toolList) {
        for (AgentTool<?> tool : toolList) {
            String toolCode = tool.getDefinition().getToolCode();
            if (tools.putIfAbsent(toolCode, tool) != null) {
                throw new IllegalStateException("Tool 编码重复注册：" + toolCode);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> AgentTool<T> require(String toolCode) {
        AgentTool<?> tool = tools.get(toolCode);
        if (tool == null) {
            throw new BizException(DdErrorCode.TOOL_NOT_FOUND);
        }
        return (AgentTool<T>) tool;
    }

    /** 已注册工具编码，按注册顺序返回 */
    public List<String> codes() {
        return List.copyOf(tools.keySet());
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(AgentTool::getDefinition).toList();
    }
}
