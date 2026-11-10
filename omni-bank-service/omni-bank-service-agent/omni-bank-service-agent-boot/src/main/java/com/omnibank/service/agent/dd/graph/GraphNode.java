package com.omnibank.service.agent.dd.graph;

import java.util.List;

/**
 * Graph 节点：Agent 节点对应一次 Agent 执行，依赖决定拓扑层级，层内节点可并行执行；
 * 汇聚节点（agentCode 为空）只做同步点，用于满足 Spring AI Alibaba Graph 的扇出汇聚约束。
 */
public record GraphNode(String nodeId, String agentCode, Object input, List<String> dependsOn) {

    public GraphNode {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public static GraphNode of(String nodeId, String agentCode, Object input, String... dependsOn) {
        return new GraphNode(nodeId, agentCode, input, List.of(dependsOn));
    }

    /** 汇聚节点：等待全部上游完成后放行下游，本身不执行 Agent */
    public static GraphNode join(String nodeId, List<String> dependsOn) {
        return new GraphNode(nodeId, null, null, dependsOn);
    }

    public boolean isJoin() {
        return agentCode == null || agentCode.isBlank();
    }
}
