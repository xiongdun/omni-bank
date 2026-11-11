package com.omnibank.service.agent.dd.graph;

import com.omnibank.service.agent.dd.ai.AgentResult;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Graph 节点执行结果：保留 Agent 原始结果与执行状态，便于追溯与下游取值
 */
@Data
public class GraphNodeResult {

    private String nodeId;

    private String agentCode;

    private GraphNodeStatus status;

    /** Agent 执行ID */
    private String executionId;

    /** Agent 结果摘要 */
    private String summary;

    /** 失败或跳过原因 */
    private String errorMessage;

    /** Agent 原始结果，跳过的节点为空 */
    private AgentResult<?> result;

    /** 是否为断点恢复复用的历史成功结果 */
    private boolean reused;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private long durationMs;
}
