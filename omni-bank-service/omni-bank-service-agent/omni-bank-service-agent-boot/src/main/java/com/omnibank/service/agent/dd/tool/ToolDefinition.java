package com.omnibank.service.agent.dd.tool;

import lombok.Data;

/**
 * Tool 定义：用于注册、发现与 Mock 标记，真实接口未确认前 Mock 工具必须置 mock=true
 */
@Data
public class ToolDefinition {

    /** 工具编码，如 enterprise.queryEnterprise */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** 职责说明 */
    private String description;

    /** 分类：QUERY / ANALYSIS / RULE / ACTION */
    private String category;

    /** 是否 Mock 实现 */
    private boolean mock;

    /** 超时时间（毫秒） */
    private long timeoutMs;

    public static ToolDefinition of(String toolCode, String toolName, String description, String category,
                                    boolean mock, long timeoutMs) {
        ToolDefinition definition = new ToolDefinition();
        definition.setToolCode(toolCode);
        definition.setToolName(toolName);
        definition.setDescription(description);
        definition.setCategory(category);
        definition.setMock(mock);
        definition.setTimeoutMs(timeoutMs);
        return definition;
    }
}
