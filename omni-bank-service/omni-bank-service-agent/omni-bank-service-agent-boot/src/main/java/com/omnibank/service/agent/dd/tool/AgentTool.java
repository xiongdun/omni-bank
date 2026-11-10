package com.omnibank.service.agent.dd.tool;

/**
 * Agent 可调用工具统一抽象：参数校验、权限与超时由后续 Tool 调用层统一实现
 */
public interface AgentTool<T> {

    ToolDefinition getDefinition();

    ToolResult<T> execute(ToolRequest request);
}
