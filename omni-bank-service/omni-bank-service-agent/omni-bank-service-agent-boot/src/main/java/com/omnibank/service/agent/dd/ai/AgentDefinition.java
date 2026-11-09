package com.omnibank.service.agent.dd.ai;

import java.util.List;
import lombok.Data;

/**
 * Agent 定义：用于注册、依赖编排与版本追溯
 */
@Data
public class AgentDefinition {

    /** Agent 编码 */
    private String agentCode;

    /** Agent 名称 */
    private String agentName;

    /** 职责说明 */
    private String description;

    /** Agent 版本 */
    private String version;

    /** Prompt 版本 */
    private String promptVersion;

    /** 输入 Schema（JSON Schema） */
    private String inputSchema;

    /** 输出 Schema（JSON Schema） */
    private String outputSchema;

    /** 依赖的上游 Agent 编码 */
    private List<String> dependencies;

    /** 所需工具编码 */
    private List<String> requiredTools;

    /** 是否启用 */
    private boolean enabled;

    /** 是否支持增量执行 */
    private boolean supportsIncrementalExecution;
}
