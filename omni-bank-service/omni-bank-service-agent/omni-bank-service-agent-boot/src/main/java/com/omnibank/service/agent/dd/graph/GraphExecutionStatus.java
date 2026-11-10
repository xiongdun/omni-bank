package com.omnibank.service.agent.dd.graph;

/**
 * Graph 整体执行状态：单个节点失败不终止整图，失败节点只影响其下游节点
 */
public enum GraphExecutionStatus {

    /** 所有节点成功（或按依赖关系跳过） */
    SUCCEEDED,

    /** 部分节点失败，其余节点仍完成执行 */
    PARTIAL_FAILED,

    /** 无任何节点成功 */
    FAILED
}
