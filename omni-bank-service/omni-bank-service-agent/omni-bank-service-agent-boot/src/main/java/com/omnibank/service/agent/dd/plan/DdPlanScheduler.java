package com.omnibank.service.agent.dd.plan;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.graph.GraphDefinition;
import com.omnibank.service.agent.dd.graph.GraphExecutor;
import com.omnibank.service.agent.dd.graph.GraphResult;
import com.omnibank.service.agent.dd.service.DdTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计划调度：任务进入数据采集后生成计划，转换为 Graph 执行，执行完成推进到分析阶段。
 * 单点失败不终止整图，异常由调用方统一标记任务失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DdPlanScheduler {

    private final DdTaskService taskService;

    private final DdPlanner planner;

    private final DdPlanGraphFactory graphFactory;

    private final GraphExecutor graphExecutor;

    public GraphResult schedule(Long taskId) {
        if (!taskService.beginDataCollecting(taskId)) {
            log.info("任务状态不是 PLANNING，跳过计划调度 taskId={}", taskId);
            return null;
        }
        DdPlan plan = planner.plan(taskId);
        GraphDefinition definition = graphFactory.build(plan);
        GraphResult result = graphExecutor.execute(definition, taskId);
        if (result == null || result.getNodes() == null || result.getNodes().isEmpty()) {
            throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED, "尽调计划未产生任何节点执行结果");
        }
        taskService.markAnalyzing(taskId);
        log.info("计划调度完成 taskId={}, planId={}, graphCode={}, status={}",
                taskId, plan.getId(), definition.getGraphCode(), result.getStatus());
        return result;
    }
}
