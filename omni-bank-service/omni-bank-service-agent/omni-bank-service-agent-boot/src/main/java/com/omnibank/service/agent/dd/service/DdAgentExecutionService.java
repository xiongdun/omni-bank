package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.execution.AgentExecutionQueryReq;
import com.omnibank.service.agent.dd.dto.execution.AgentExecutionVO;
import com.omnibank.service.agent.dd.graph.GraphNode;
import com.omnibank.service.agent.dd.graph.GraphNodeResult;
import java.util.Map;

/**
 * Agent 执行记录服务：节点执行落库，并为断点恢复提供历史成功结果
 */
public interface DdAgentExecutionService {

    /** 记录一次节点执行结果（汇聚节点不记录） */
    void record(Long taskId, Long planId, String graphCode, GraphNode node, GraphNodeResult nodeResult);

    /** 读取任务在同一 Graph 下每个节点的最新成功执行结果，节点ID -> 结果 */
    Map<String, GraphNodeResult> loadSucceeded(Long taskId, String graphCode);

    /** 任务执行记录分页 */
    IPage<AgentExecutionVO> page(Long taskId, AgentExecutionQueryReq req);
}
