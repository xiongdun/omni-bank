package com.omnibank.service.agent.dd.graph;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Graph 定义：按业务场景编排的 Agent 执行图，节点间依赖不得形成环
 */
@Data
public class GraphDefinition {

    /** Graph 编码，用于执行记录与问题定位 */
    private String graphCode;

    /** Graph 名称 */
    private String name;

    /** 节点集合 */
    private List<GraphNode> nodes = new ArrayList<>();
}
