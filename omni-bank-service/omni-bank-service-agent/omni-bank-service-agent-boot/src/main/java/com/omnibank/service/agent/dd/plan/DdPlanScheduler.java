package com.omnibank.service.agent.dd.plan;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.graph.GraphDefinition;
import com.omnibank.service.agent.dd.graph.GraphExecutor;
import com.omnibank.service.agent.dd.graph.GraphResult;
import com.omnibank.service.agent.dd.mapper.DdPlanMapper;
import com.omnibank.service.agent.dd.service.DdTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计划调度：任务进入数据采集后生成计划，转换为 Graph 执行，执行完成推进到分析阶段。
 * 失败任务可按当前计划断点恢复：历史成功节点复用，失败与跳过节点重新执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DdPlanScheduler {

    private final DdTaskService taskService;

    private final DdPlanner planner;

    private final DdPlanGraphFactory graphFactory;

    private final GraphExecutor graphExecutor;

    private final DdPlanMapper planMapper;

    public GraphResult schedule(Long taskId) {
        if (!taskService.beginDataCollecting(taskId)) {
            log.info("任务状态不是 PLANNING，跳过计划调度 taskId={}", taskId);
            return null;
        }
        DdPlan plan = planner.plan(taskId);
        GraphDefinition definition = graphFactory.build(plan);
        GraphResult result = graphExecutor.execute(definition, taskId, plan.getId(), false);
        if (result == null || result.getNodes() == null || result.getNodes().isEmpty()) {
            throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED, "尽调计划未产生任何节点执行结果");
        }
        taskService.markAnalyzing(taskId);
        log.info("计划调度完成 taskId={}, planId={}, graphCode={}, status={}",
                taskId, plan.getId(), definition.getGraphCode(), result.getStatus());
        return result;
    }

    /** 失败任务断点恢复：按当前计划重跑 Graph，已成功节点直接复用历史结果 */
    public GraphResult resume(Long taskId) {
        DdTask task = taskService.requireTask(taskId);
        if (!TaskStatus.FAILED.name().equals(task.getTaskStatus())) {
            throw new BizException(DdErrorCode.TASK_NOT_RESUMABLE, "仅失败状态的任务可以恢复执行");
        }
        if (task.getCurrentPlanId() == null) {
            throw new BizException(DdErrorCode.TASK_NOT_RESUMABLE, "任务没有可恢复的尽调计划");
        }
        DdPlan plan = planMapper.selectById(task.getCurrentPlanId());
        if (plan == null) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "任务当前计划不存在，无法恢复执行");
        }
        GraphDefinition definition = graphFactory.build(plan);
        if (!taskService.beginResume(taskId)) {
            throw new BizException(DdErrorCode.TASK_STATUS_CONFLICT);
        }
        try {
            GraphResult result = graphExecutor.execute(definition, taskId, plan.getId(), true);
            log.info("任务恢复执行完成 taskId={}, planId={}, graphCode={}, status={}",
                    taskId, plan.getId(), definition.getGraphCode(), result.getStatus());
            return result;
        } catch (BizException e) {
            taskService.markFailed(taskId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("任务恢复执行失败 taskId={}, planId={}", taskId, plan.getId(), e);
            taskService.markFailed(taskId, DdErrorCode.GRAPH_EXECUTION_FAILED.getCode(), e.getMessage());
            throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED,
                    "任务恢复执行失败：" + e.getMessage());
        }
    }
}
