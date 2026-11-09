package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.event.DdOutboxEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 任务初始化消费者：收到 TASK_SUBMITTED 后推进任务到场景识别并生成场景识别结果。
 * 幂等依据任务当前状态，而不是事件是否重复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DdTaskInitHandler {

    private static final String EVENT_TASK_SUBMITTED = "TASK_SUBMITTED";

    private final DdTaskService taskService;

    private final DdScenarioService scenarioService;

    @EventListener
    public void handle(DdOutboxEventMessage message) {
        if (!EVENT_TASK_SUBMITTED.equals(message.eventType())) {
            return;
        }
        Long taskId = message.aggregateId();
        DdTask task = taskService.requireTask(taskId);
        if (!TaskStatus.INITIALIZING.name().equals(task.getTaskStatus())) {
            log.info("任务当前状态无需初始化，忽略事件 taskId={}, status={}", taskId, task.getTaskStatus());
            return;
        }
        if (!taskService.beginScenarioIdentify(taskId)) {
            log.info("任务初始化已被其他执行者推进 taskId={}", taskId);
            return;
        }
        try {
            scenarioService.identify(taskId);
            taskService.markPlanning(taskId);
        } catch (Exception e) {
            log.error("任务初始化失败 taskId={}", taskId, e);
            taskService.markFailed(taskId, DdErrorCode.SCENARIO_IDENTIFY_FAILED.getCode(),
                    e.getMessage() == null ? DdErrorCode.SCENARIO_IDENTIFY_FAILED.getMsg() : e.getMessage());
        }
    }
}
