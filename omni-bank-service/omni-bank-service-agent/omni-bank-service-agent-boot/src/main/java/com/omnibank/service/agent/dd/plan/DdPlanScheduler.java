package com.omnibank.service.agent.dd.plan;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.entity.DdSupplementRequest;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.SupplementRequestStatus;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.event.DdReanalysisRequestedEvent;
import com.omnibank.service.agent.dd.graph.GraphDefinition;
import com.omnibank.service.agent.dd.graph.GraphExecutor;
import com.omnibank.service.agent.dd.graph.GraphResult;
import com.omnibank.service.agent.dd.mapper.DdPlanMapper;
import com.omnibank.service.agent.dd.mapper.DdSupplementRequestMapper;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
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

    private final DdSupplementRequestMapper supplementRequestMapper;

    private final DdJsonSupport jsonSupport;

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
        advanceAfterAnalysis(taskId);
        log.info("计划调度完成 taskId={}, planId={}, graphCode={}, status={}",
                taskId, plan.getId(), definition.getGraphCode(), result.getStatus());
        return result;
    }

    /**
     * 分析完成后的状态推进：存在待补充资料停在待补充，否则进入风险评估、报告生成与待复核。
     */
    private void advanceAfterAnalysis(Long taskId) {
        Long pending = supplementRequestMapper.selectCount(Wrappers.<DdSupplementRequest>lambdaQuery()
                .eq(DdSupplementRequest::getTaskId, taskId)
                .eq(DdSupplementRequest::getStatus, SupplementRequestStatus.PENDING.name()));
        if (pending != null && pending > 0) {
            taskService.markWaitingForSupplement(taskId);
            return;
        }
        taskService.markRiskAssessing(taskId);
        taskService.markReportGenerating(taskId);
        taskService.markWaitingForReview(taskId);
    }

    /** 补充材料完成或报告要求重分析：复用未受影响节点，重跑受影响 Agent 及其下游 */
    @EventListener
    public void onReanalysisRequested(DdReanalysisRequestedEvent event) {
        reanalyze(event.taskId(), event.agentCodes());
    }

    /**
     * 增量重分析：按受影响 Agent 计算下游闭包，仅这些节点重新执行，其余复用历史成功结果。
     *
     * @param impactedAgents 受影响 Agent；为空表示全量重分析
     */
    public GraphResult reanalyze(Long taskId, Set<String> impactedAgents) {
        DdTask task = taskService.requireTask(taskId);
        if (task.getCurrentPlanId() == null) {
            throw new BizException(DdErrorCode.PLAN_NOT_FOUND, "任务没有可重分析的尽调计划");
        }
        DdPlan plan = planMapper.selectById(task.getCurrentPlanId());
        if (plan == null) {
            throw new BizException(DdErrorCode.PLAN_NOT_FOUND, "任务当前计划不存在，无法重分析");
        }
        GraphDefinition definition = graphFactory.build(plan);
        Set<String> rerunAgents = downstreamClosure(plan, impactedAgents);
        if (!taskService.beginReanalyzing(taskId)) {
            throw new BizException(DdErrorCode.TASK_STATUS_NOT_ALLOWED,
                    "任务当前状态「" + task.getTaskStatus() + "」不允许重新分析");
        }
        try {
            GraphResult result = graphExecutor.execute(definition, taskId, plan.getId(), rerunAgents);
            if (result == null || result.getNodes() == null || result.getNodes().isEmpty()) {
                throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED, "增量重分析未产生任何节点执行结果");
            }
            advanceAfterAnalysis(taskId);
            log.info("增量重分析完成 taskId={}, planId={}, rerunAgents={}, status={}",
                    taskId, plan.getId(), rerunAgents, result.getStatus());
            return result;
        } catch (BizException e) {
            taskService.markFailed(taskId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("增量重分析失败 taskId={}, planId={}", taskId, plan.getId(), e);
            taskService.markFailed(taskId, DdErrorCode.GRAPH_EXECUTION_FAILED.getCode(), e.getMessage());
            throw new BizException(DdErrorCode.GRAPH_EXECUTION_FAILED,
                    "增量重分析失败：" + e.getMessage());
        }
    }

    /**
     * 计算下游闭包：受影响 Agent 自身 + 所有传递依赖它的 Agent。计划缺少 Agent 信息时返回 null 表示全量执行。
     */
    private Set<String> downstreamClosure(DdPlan plan, Set<String> impactedAgents) {
        List<String> agents = jsonSupport.readStringList(plan.getAgentPlan());
        if (agents == null || agents.isEmpty() || impactedAgents == null || impactedAgents.isEmpty()) {
            return null;
        }
        Map<String, List<String>> dependencies = jsonSupport.readStringListMap(plan.getDependencyPlan());
        Set<String> closure = new LinkedHashSet<>();
        impactedAgents.stream().filter(agents::contains).forEach(closure::add);
        if (closure.isEmpty()) {
            return null;
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String agent : agents) {
                if (closure.contains(agent)) {
                    continue;
                }
                List<String> upstream = dependencies.getOrDefault(agent, List.of());
                if (upstream.stream().anyMatch(closure::contains)) {
                    closure.add(agent);
                    changed = true;
                }
            }
        }
        return closure;
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
