package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.dto.execution.AgentExecutionQueryReq;
import com.omnibank.service.agent.dd.dto.execution.AgentExecutionVO;
import com.omnibank.service.agent.dd.entity.DdAgentExecution;
import com.omnibank.service.agent.dd.graph.GraphNode;
import com.omnibank.service.agent.dd.graph.GraphNodeResult;
import com.omnibank.service.agent.dd.graph.GraphNodeStatus;
import com.omnibank.service.agent.dd.mapper.DdAgentExecutionMapper;
import com.omnibank.service.agent.dd.service.DdAgentExecutionService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Agent 执行记录实现：按任务 + Graph + 节点递增执行序号，结果快照以 JSON 保存；
 * 恢复执行时仅复用最新一条成功记录，失败与跳过节点重新执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdAgentExecutionServiceImpl
        extends ServiceImpl<DdAgentExecutionMapper, DdAgentExecution>
        implements DdAgentExecutionService {

    private static final int SUMMARY_MAX_LENGTH = 1000;

    private static final TypeReference<AgentResult<Object>> RESULT_TYPE =
            new TypeReference<AgentResult<Object>>() {
            };

    private final DdJsonSupport jsonSupport;

    private final DdTaskService taskService;

    @Override
    public void record(Long taskId, Long planId, String graphCode, GraphNode node,
                       GraphNodeResult nodeResult) {
        if (node.isJoin()) {
            return;
        }
        DdAgentExecution entity = new DdAgentExecution();
        entity.setTaskId(taskId);
        entity.setPlanId(planId);
        entity.setGraphCode(graphCode);
        entity.setNodeId(node.nodeId());
        entity.setAgentCode(node.agentCode());
        entity.setExecutionNo(nextExecutionNo(taskId, graphCode, node.nodeId()));
        entity.setExecutionStatus(nodeResult.getStatus().name());
        entity.setSummary(truncate(nodeResult.getSummary()));
        entity.setErrorMessage(truncate(nodeResult.getErrorMessage()));
        entity.setInputSnapshot(node.input() == null ? null : jsonSupport.write(node.input()));
        AgentResult<?> result = nodeResult.getResult();
        if (result != null) {
            entity.setResultContent(jsonSupport.write(result));
            entity.setModelVersion(result.getModelVersion());
            entity.setPromptVersion(result.getPromptVersion());
        }
        entity.setStartedAt(nodeResult.getStartedAt());
        entity.setFinishedAt(nodeResult.getFinishedAt());
        entity.setDurationMs(nodeResult.getDurationMs());
        entity.setVersion(0);
        save(entity);
    }

    @Override
    public Map<String, GraphNodeResult> loadSucceeded(Long taskId, String graphCode) {
        List<DdAgentExecution> records = lambdaQuery()
                .eq(DdAgentExecution::getTaskId, taskId)
                .eq(DdAgentExecution::getGraphCode, graphCode)
                .eq(DdAgentExecution::getExecutionStatus, GraphNodeStatus.SUCCEEDED.name())
                .orderByDesc(DdAgentExecution::getExecutionNo)
                .list();
        Map<String, GraphNodeResult> reusable = new LinkedHashMap<>();
        for (DdAgentExecution record : records) {
            if (reusable.containsKey(record.getNodeId()) || !StringUtils.hasText(record.getResultContent())) {
                continue;
            }
            AgentResult<Object> result = jsonSupport.read(record.getResultContent(), RESULT_TYPE);
            if (result == null) {
                continue;
            }
            GraphNodeResult nodeResult = new GraphNodeResult();
            nodeResult.setNodeId(record.getNodeId());
            nodeResult.setAgentCode(record.getAgentCode());
            nodeResult.setStatus(GraphNodeStatus.SUCCEEDED);
            nodeResult.setExecutionId(result.getExecutionId());
            nodeResult.setSummary(record.getSummary());
            nodeResult.setResult(result);
            nodeResult.setReused(true);
            nodeResult.setStartedAt(record.getStartedAt());
            nodeResult.setFinishedAt(record.getFinishedAt());
            nodeResult.setDurationMs(record.getDurationMs() == null ? 0L : record.getDurationMs());
            reusable.put(record.getNodeId(), nodeResult);
        }
        return reusable;
    }

    @Override
    public IPage<AgentExecutionVO> page(Long taskId, AgentExecutionQueryReq req) {
        taskService.requireTask(taskId);
        return lambdaQuery()
                .eq(DdAgentExecution::getTaskId, taskId)
                .eq(StringUtils.hasText(req.getExecutionStatus()),
                        DdAgentExecution::getExecutionStatus, req.getExecutionStatus())
                .eq(StringUtils.hasText(req.getNodeId()), DdAgentExecution::getNodeId, req.getNodeId())
                .orderByDesc(DdAgentExecution::getId)
                .page(new Page<>(req.getCurrent(), req.getSize()))
                .convert(this::toVO);
    }

    /** 执行序号按任务 + Graph + 节点维度递增，唯一键兜底并发重复 */
    private int nextExecutionNo(Long taskId, String graphCode, String nodeId) {
        DdAgentExecution latest = lambdaQuery()
                .eq(DdAgentExecution::getTaskId, taskId)
                .eq(DdAgentExecution::getGraphCode, graphCode)
                .eq(DdAgentExecution::getNodeId, nodeId)
                .orderByDesc(DdAgentExecution::getExecutionNo)
                .last("LIMIT 1")
                .one();
        return latest == null ? 1 : latest.getExecutionNo() + 1;
    }

    private AgentExecutionVO toVO(DdAgentExecution entity) {
        AgentExecutionVO vo = new AgentExecutionVO();
        vo.setExecutionId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setPlanId(entity.getPlanId());
        vo.setGraphCode(entity.getGraphCode());
        vo.setNodeId(entity.getNodeId());
        vo.setAgentCode(entity.getAgentCode());
        vo.setExecutionNo(entity.getExecutionNo());
        vo.setExecutionStatus(entity.getExecutionStatus());
        vo.setSummary(entity.getSummary());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setModelVersion(entity.getModelVersion());
        vo.setPromptVersion(entity.getPromptVersion());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setDurationMs(entity.getDurationMs());
        return vo;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
