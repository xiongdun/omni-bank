package com.omnibank.service.agent.dd.plan;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.graph.GraphDefinition;
import com.omnibank.service.agent.dd.graph.GraphNode;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 计划转 Graph：按依赖计算拓扑层级，同层 Agent 并行，层后统一汇聚。
 * 汇聚节点用于满足 Spring AI Alibaba Graph 的扇出约束（多个后继必须汇合到同一直接后继）。
 */
@Component
@RequiredArgsConstructor
public class DdPlanGraphFactory {

    private static final String GRAPH_NAME = "对公信贷智能尽调编排图";

    private final DdJsonSupport jsonSupport;

    public GraphDefinition build(DdPlan plan) {
        List<String> agents = jsonSupport.readStringList(plan.getAgentPlan());
        Map<String, List<String>> dependencies = jsonSupport.readStringListMap(plan.getDependencyPlan());
        return build("DD_DILIGENCE_" + plan.getTaskId(), agents, dependencies);
    }

    public GraphDefinition build(String graphCode, List<String> agentCodes, Map<String, List<String>> dependencies) {
        Set<String> agents = new LinkedHashSet<>();
        if (agentCodes != null) {
            for (String agentCode : agentCodes) {
                if (agentCode == null || agentCode.isBlank()) {
                    continue;
                }
                if (!DdAgentCatalog.contains(agentCode)) {
                    throw new BizException(DdErrorCode.PLAN_INVALID, "计划包含未登记的 Agent：" + agentCode);
                }
                agents.add(agentCode);
            }
        }
        if (agents.isEmpty()) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "计划未包含任何 Agent，无法调度");
        }

        Map<String, List<String>> normalized = normalizeDependencies(agents, dependencies);
        List<List<String>> levels = DdAgentCatalog.levels(new ArrayList<>(agents), normalized);
        if (levels.isEmpty()) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "计划依赖存在循环，无法调度");
        }

        GraphDefinition definition = new GraphDefinition();
        definition.setGraphCode(graphCode);
        definition.setName(GRAPH_NAME);
        List<GraphNode> nodes = new ArrayList<>();
        String previousJoin = null;
        for (int level = 0; level < levels.size(); level++) {
            List<String> layerAgents = levels.get(level);
            for (String agentCode : layerAgents) {
                nodes.add(previousJoin == null
                        ? GraphNode.of(agentCode, agentCode, null)
                        : GraphNode.of(agentCode, agentCode, null, previousJoin));
            }
            String joinId = "join." + level;
            nodes.add(GraphNode.join(joinId, layerAgents));
            previousJoin = joinId;
        }
        definition.setNodes(nodes);
        return definition;
    }

    /** 依赖只保留计划内 Agent；未声明依赖时回退 Agent 目录的初始依赖 */
    private Map<String, List<String>> normalizeDependencies(Set<String> agents,
                                                            Map<String, List<String>> dependencies) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        Map<String, List<String>> declared = dependencies == null || dependencies.isEmpty()
                ? DdAgentCatalog.defaultDependencies() : dependencies;
        for (String agent : agents) {
            List<String> dependenciesOfAgent = new ArrayList<>();
            for (String dependency : declared.getOrDefault(agent, List.of())) {
                if (dependency != null && agents.contains(dependency) && !dependency.equals(agent)
                        && !dependenciesOfAgent.contains(dependency)) {
                    dependenciesOfAgent.add(dependency);
                }
            }
            normalized.put(agent, dependenciesOfAgent);
        }
        return normalized;
    }
}
