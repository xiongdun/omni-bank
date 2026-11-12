package com.omnibank.service.agent.dd.event;

import java.util.Set;

/**
 * 增量重分析请求事件：补充材料完成或报告要求重新分析时发布，
 * 由计划调度监听后复用未受影响节点、重跑受影响 Agent 及其下游。
 *
 * @param taskId      任务ID
 * @param agentCodes  受影响的 Agent 编码；为空表示全量重分析
 * @param reason      触发原因，用于日志
 */
public record DdReanalysisRequestedEvent(Long taskId, Set<String> agentCodes, String reason) {
}
