package com.omnibank.service.agent.dd.tool;

/**
 * Agent 可调用工具统一抽象：参数校验、超时控制与调用审计由 ToolInvoker 统一实现
 */
public interface AgentTool<T> {

    ToolDefinition getDefinition();

    ToolResult<T> execute(ToolRequest request);
}
