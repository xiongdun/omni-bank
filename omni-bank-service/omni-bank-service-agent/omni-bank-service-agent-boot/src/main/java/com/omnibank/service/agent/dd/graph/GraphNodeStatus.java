package com.omnibank.service.agent.dd.graph;

/**
 * Graph 节点执行状态：SKIPPED 表示因上游节点未成功而未执行；NOT_DELIVERED 表示 Agent 尚未交付，不调用运行时且不伪造结果
 */
public enum GraphNodeStatus {

    PENDING,

    RUNNING,

    SUCCEEDED,

    FAILED,

    SKIPPED,

    NOT_DELIVERED
}
