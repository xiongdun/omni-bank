package com.omnibank.service.agent.dd.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentRegistry;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.service.DdAgentExecutionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Agent 编排执行器：基于 Spring AI Alibaba Graph（StateGraph / CompiledGraph）实现。
 * 业务 Graph 定义转换为 StateGraph 的节点与边，由框架负责依赖等待与并行调度；
 * 节点动作仅负责调用 AgentRuntime，并在上游未成功时将自身标记 SKIPPED，避免单点失败终止整图。
 * 每个 Agent 节点的执行结果落库；恢复执行时复用最新成功记录，失败与跳过节点重新执行。
 */
@Slf4j
@Component
public class GraphExecutor {

    private static final String RESULT_KEY_PREFIX = "result.";

    private final AgentRuntime agentRuntime;

    private final AgentRegistry agentRegistry;

    private final DdAgentExecutionService executionService;

    private final Executor executor;

    public GraphExecutor(AgentRuntime agentRuntime, AgentRegistry agentRegistry,
                         DdAgentExecutionService executionService,
                         @Qualifier("ttlExecutor") Executor executor) {
        this.agentRuntime = agentRuntime;
        this.agentRegistry = agentRegistry;
        this.executionService = executionService;
        this.executor = executor;
    }

    public GraphResult execute(GraphDefinition definition, Long taskId) {
        return execute(definition, taskId, null, false);
    }

    /**
     * 执行 Graph。
     *
     * @param planId 关联计划ID，直接调度时为空
     * @param resume 是否断点恢复：复用历史成功节点结果，其余节点重新执行
     */
    public GraphResult execute(GraphDefinition definition, Long taskId, Long planId, boolean resume) {
        List<GraphNode> nodes = validate(definition);
        GraphResult graphResult = new GraphResult();
        graphResult.setGraphCode(definition.getGraphCode());
        graphResult.setTaskId(taskId);
        graphResult.setStartedAt(LocalDateTime.now());
        long start = System.nanoTime();

        try {
            // 节点结果在框架执行过程中直接收集：Graph 返回的终态经过 StateSerializer 序列化克隆，
            // 自定义结果对象会被还原为 Map，无法按类型读取
            Map<String, GraphNodeResult> collected = new ConcurrentHashMap<>();
            Map<String, GraphNodeResult> reusable = resume
                    ? executionService.loadSucceeded(taskId, definition.getGraphCode()) : Map.of();
            CompiledGraph compiledGraph = buildGraph(definition.getGraphCode(), nodes, taskId, planId,
                    reusable, collected);
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    // 并行节点统一走 TTL 装饰的业务线程池，保证用户上下文与追踪信息可传递
                    .defaultParallelExecutor(executor)
                    .build();
            Optional<OverAllState> finalState = compiledGraph.invoke(Map.of(), runnableConfig);
            if (finalState.isEmpty()) {
                throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED, "Graph 未返回执行状态");
            }
            Map<String, GraphNodeResult> results = collectResults(collected, nodes);
            graphResult.setNodes(results);
            graphResult.setStatus(overallStatus(results));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Graph 执行失败 graphCode={}, taskId={}", definition.getGraphCode(), taskId, e);
            throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED,
                    "Graph 执行失败：" + e.getMessage());
        }

        graphResult.setFinishedAt(LocalDateTime.now());
        graphResult.setDurationMs((System.nanoTime() - start) / 1_000_000);
        log.info("Graph 执行完成 graphCode={}, taskId={}, status={}, nodes={}, durationMs={}",
                definition.getGraphCode(), taskId, graphResult.getStatus(),
                graphResult.getNodes().size(), graphResult.getDurationMs());
        return graphResult;
    }

    private CompiledGraph buildGraph(String graphCode, List<GraphNode> nodes, Long taskId, Long planId,
                                     Map<String, GraphNodeResult> reusable,
                                     Map<String, GraphNodeResult> collected) {
        Map<String, KeyStrategy> keyStrategies = new HashMap<>();
        nodes.forEach(node -> keyStrategies.put(resultKey(node.nodeId()), new ReplaceStrategy()));
        StateGraph stateGraph = new StateGraph(graphCode, () -> keyStrategies);
        try {
            for (GraphNode node : nodes) {
                stateGraph.addNode(node.nodeId(), AsyncNodeAction.node_async(
                        state -> executeNode(state, node, taskId, planId, graphCode, reusable, collected)));
            }

            Set<String> dependents = new HashSet<>();
            for (GraphNode node : nodes) {
                if (node.dependsOn().isEmpty()) {
                    stateGraph.addEdge(StateGraph.START, node.nodeId());
                } else {
                    // 多依赖入边：框架在该节点所有上游完成后才触发
                    stateGraph.addEdge(node.dependsOn(), node.nodeId());
                    dependents.addAll(node.dependsOn());
                }
            }
            for (GraphNode node : nodes) {
                if (!dependents.contains(node.nodeId())) {
                    stateGraph.addEdge(node.nodeId(), StateGraph.END);
                }
            }
            return stateGraph.compile();
        } catch (GraphStateException e) {
            throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 编译失败：" + e.getMessage());
        }
    }

    private Map<String, Object> executeNode(OverAllState state, GraphNode node, Long taskId, Long planId,
                                            String graphCode, Map<String, GraphNodeResult> reusable,
                                            Map<String, GraphNodeResult> collected) {
        Map<String, GraphNodeResult> dependencyResults = new LinkedHashMap<>();
        for (String dependency : node.dependsOn()) {
            state.value(resultKey(dependency), GraphNodeResult.class)
                    .ifPresent(result -> dependencyResults.put(dependency, result));
        }
        GraphNodeResult nodeResult;
        if (node.isJoin()) {
            // 汇聚节点只做同步点，上游失败或未交付时仍然放行，由下游按数据缺口自行处理
            nodeResult = joinResult(node, dependencyResults);
        } else {
            GraphNodeResult previous = reusable.get(node.nodeId());
            String blockedDependency = blockedDependency(node, dependencyResults);
            if (previous != null) {
                // 断点恢复：历史成功节点直接复用结果，不再调用 Agent，也不重复写执行记录
                nodeResult = reusedResult(previous);
            } else if (blockedDependency != null) {
                nodeResult = skipped(node, blockedDependency);
            } else if (!agentRegistry.contains(node.agentCode())) {
                nodeResult = notDelivered(node);
            } else {
                nodeResult = executeNodeInternal(node, taskId, upstream(dependencyResults));
            }
            if (previous == null) {
                executionService.record(taskId, planId, graphCode, node, nodeResult);
            }
        }
        collected.put(node.nodeId(), nodeResult);
        return Map.of(resultKey(node.nodeId()), nodeResult);
    }

    private GraphNodeResult reusedResult(GraphNodeResult previous) {
        GraphNodeResult nodeResult = new GraphNodeResult();
        nodeResult.setNodeId(previous.getNodeId());
        nodeResult.setAgentCode(previous.getAgentCode());
        nodeResult.setStatus(GraphNodeStatus.SUCCEEDED);
        nodeResult.setExecutionId(previous.getExecutionId());
        nodeResult.setSummary(previous.getSummary());
        nodeResult.setResult(previous.getResult());
        nodeResult.setReused(true);
        nodeResult.setStartedAt(previous.getStartedAt());
        nodeResult.setFinishedAt(previous.getFinishedAt());
        nodeResult.setDurationMs(previous.getDurationMs());
        return nodeResult;
    }

    /** 下游可见的上游结果：普通节点按节点ID注入，汇聚节点展开其聚合结果 */
    private Map<String, Object> upstream(Map<String, GraphNodeResult> dependencyResults) {
        Map<String, Object> upstream = new LinkedHashMap<>();
        dependencyResults.forEach((nodeId, upstreamResult) -> {
            if (upstreamResult.getAgentCode() == null && upstreamResult.getResult() != null
                    && upstreamResult.getResult().getData() instanceof Map<?, ?> aggregated) {
                aggregated.forEach((key, value) -> {
                    if (key instanceof String code) {
                        upstream.put(code, value);
                    }
                });
            } else if (upstreamResult.getResult() != null) {
                upstream.put(nodeId, upstreamResult.getResult());
            }
        });
        return upstream;
    }

    private GraphNodeResult joinResult(GraphNode node, Map<String, GraphNodeResult> dependencyResults) {
        LocalDateTime now = LocalDateTime.now();
        GraphNodeResult nodeResult = baseResult(node, GraphNodeStatus.SUCCEEDED, now);
        Map<String, Object> aggregated = new LinkedHashMap<>();
        long succeeded = 0;
        long failed = 0;
        long notDelivered = 0;
        for (Map.Entry<String, GraphNodeResult> entry : dependencyResults.entrySet()) {
            GraphNodeResult dependency = entry.getValue();
            if (dependency.getStatus() == GraphNodeStatus.SUCCEEDED) {
                succeeded++;
                if (dependency.getResult() != null) {
                    aggregated.put(entry.getKey(), dependency.getResult());
                }
            } else if (dependency.getStatus() == GraphNodeStatus.NOT_DELIVERED) {
                notDelivered++;
            } else {
                failed++;
            }
        }
        AgentResult<Map<String, Object>> result = new AgentResult<>();
        result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
        result.setData(aggregated);
        result.setSummary("汇聚 " + dependencyResults.size() + " 个上游节点：成功 " + succeeded
                + "，失败/跳过 " + failed + "，未交付 " + notDelivered);
        nodeResult.setResult(result);
        nodeResult.setSummary(result.getSummary());
        nodeResult.setFinishedAt(now);
        nodeResult.setDurationMs(0L);
        return nodeResult;
    }

    private GraphNodeResult notDelivered(GraphNode node) {
        LocalDateTime now = LocalDateTime.now();
        GraphNodeResult nodeResult = baseResult(node, GraphNodeStatus.NOT_DELIVERED, now);
        nodeResult.setSummary("Agent 未交付，未执行");
        nodeResult.setErrorMessage("Agent 未交付，未执行：" + node.agentCode());
        nodeResult.setFinishedAt(now);
        return nodeResult;
    }

    private GraphNodeResult executeNodeInternal(GraphNode node, Long taskId, Map<String, Object> upstream) {
        LocalDateTime startedAt = LocalDateTime.now();
        long start = System.nanoTime();
        GraphNodeResult nodeResult = baseResult(node, GraphNodeStatus.RUNNING, startedAt);
        try {
            AgentResult<?> result = agentRuntime.execute(node.agentCode(), taskId, node.input(), upstream);
            nodeResult.setResult(result);
            nodeResult.setExecutionId(result.getExecutionId());
            nodeResult.setSummary(result.getSummary());
            boolean succeeded = AgentExecutionStatus.SUCCEEDED.name().equals(result.getStatus());
            nodeResult.setStatus(succeeded ? GraphNodeStatus.SUCCEEDED : GraphNodeStatus.FAILED);
            if (!succeeded) {
                nodeResult.setErrorMessage(result.getSummary());
            }
        } catch (Exception e) {
            log.error("Graph 节点执行异常 nodeId={}, agentCode={}", node.nodeId(), node.agentCode(), e);
            nodeResult.setStatus(GraphNodeStatus.FAILED);
            nodeResult.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        nodeResult.setFinishedAt(LocalDateTime.now());
        nodeResult.setDurationMs((System.nanoTime() - start) / 1_000_000);
        return nodeResult;
    }

    private Map<String, GraphNodeResult> collectResults(Map<String, GraphNodeResult> collected, List<GraphNode> nodes) {
        Map<String, GraphNodeResult> results = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            results.put(node.nodeId(), collected.getOrDefault(node.nodeId(), missingResult(node)));
        }
        return results;
    }

    private GraphNodeResult missingResult(GraphNode node) {
        LocalDateTime now = LocalDateTime.now();
        GraphNodeResult nodeResult = baseResult(node, GraphNodeStatus.FAILED, now);
        nodeResult.setErrorMessage("节点未产生执行结果");
        nodeResult.setFinishedAt(now);
        return nodeResult;
    }

    private GraphNodeResult skipped(GraphNode node, String blockedDependency) {
        LocalDateTime now = LocalDateTime.now();
        GraphNodeResult nodeResult = baseResult(node, GraphNodeStatus.SKIPPED, now);
        nodeResult.setErrorMessage("依赖节点未成功，节点跳过：" + blockedDependency);
        nodeResult.setFinishedAt(now);
        return nodeResult;
    }

    private GraphNodeResult baseResult(GraphNode node, GraphNodeStatus status, LocalDateTime startedAt) {
        GraphNodeResult nodeResult = new GraphNodeResult();
        nodeResult.setNodeId(node.nodeId());
        nodeResult.setAgentCode(node.agentCode());
        nodeResult.setStatus(status);
        nodeResult.setStartedAt(startedAt);
        return nodeResult;
    }

    private String blockedDependency(GraphNode node, Map<String, GraphNodeResult> results) {
        for (String dependency : node.dependsOn()) {
            GraphNodeResult dependencyResult = results.get(dependency);
            if (dependencyResult == null || dependencyResult.getStatus() != GraphNodeStatus.SUCCEEDED) {
                return dependency;
            }
        }
        return null;
    }

    private GraphExecutionStatus overallStatus(Map<String, GraphNodeResult> results) {
        long succeeded = results.values().stream()
                .filter(node -> node.getStatus() == GraphNodeStatus.SUCCEEDED).count();
        long failed = results.values().stream()
                .filter(node -> node.getStatus() == GraphNodeStatus.FAILED).count();
        if (failed == 0) {
            return GraphExecutionStatus.SUCCEEDED;
        }
        return succeeded == 0 ? GraphExecutionStatus.FAILED : GraphExecutionStatus.PARTIAL_FAILED;
    }

    private List<GraphNode> validate(GraphDefinition definition) {
        if (definition == null || definition.getGraphCode() == null || definition.getGraphCode().isBlank()) {
            throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 定义缺少编码");
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 未定义节点：" + definition.getGraphCode());
        }
        if (definition.getNodes().stream().anyMatch(Objects::isNull)) {
            throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 存在空节点：" + definition.getGraphCode());
        }
        List<GraphNode> nodes = List.copyOf(definition.getNodes());
        Set<String> nodeIds = new LinkedHashSet<>();
        for (GraphNode node : nodes) {
            if (node.nodeId() == null || node.nodeId().isBlank()) {
                throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 节点缺少 nodeId");
            }
            if (!nodeIds.add(node.nodeId())) {
                throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 节点ID重复：" + node.nodeId());
            }
            if (node.agentCode() == null || node.agentCode().isBlank()) {
                if (!node.isJoin()) {
                    throw new BizException(DdErrorCode.GRAPH_INVALID,
                            "Graph 节点缺少 agentCode：" + node.nodeId());
                }
                if (node.dependsOn().isEmpty()) {
                    throw new BizException(DdErrorCode.GRAPH_INVALID,
                            "Graph 汇聚节点缺少上游：" + node.nodeId());
                }
                continue;
            }
        }
        for (GraphNode node : nodes) {
            for (String dependency : node.dependsOn()) {
                if (dependency.equals(node.nodeId())) {
                    throw new BizException(DdErrorCode.GRAPH_INVALID,
                            "Graph 节点不能依赖自身：" + node.nodeId());
                }
                if (!nodeIds.contains(dependency)) {
                    throw new BizException(DdErrorCode.GRAPH_INVALID,
                            "Graph 节点依赖不存在：" + node.nodeId() + " -> " + dependency);
                }
            }
        }
        detectCycle(nodes);
        return nodes;
    }

    /** 提前校验环依赖并给出明确错误，避免编译期异常信息不可读 */
    private void detectCycle(List<GraphNode> nodes) {
        Map<String, GraphNode> nodeIndex = new LinkedHashMap<>();
        nodes.forEach(node -> nodeIndex.put(node.nodeId(), node));
        List<String> remaining = new ArrayList<>(nodeIndex.keySet());
        Set<String> completed = new LinkedHashSet<>();
        while (!remaining.isEmpty()) {
            List<GraphNode> layer = remaining.stream()
                    .map(nodeIndex::get)
                    .filter(node -> completed.containsAll(node.dependsOn()))
                    .toList();
            if (layer.isEmpty()) {
                throw new BizException(DdErrorCode.GRAPH_INVALID, "Graph 存在循环依赖：" + remaining);
            }
            layer.forEach(node -> completed.add(node.nodeId()));
            remaining.removeAll(layer.stream().map(GraphNode::nodeId).toList());
        }
    }

    private String resultKey(String nodeId) {
        return RESULT_KEY_PREFIX + nodeId;
    }
}
