package com.omnibank.service.agent.dd.graph;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * Graph 执行结果：节点结果按定义顺序聚合，整体状态反映失败范围
 */
@Data
public class GraphResult {

    private String graphCode;

    private Long taskId;

    private GraphExecutionStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private long durationMs;

    /** 节点ID -> 执行结果，保持定义顺序 */
    private Map<String, GraphNodeResult> nodes = new LinkedHashMap<>();
}
