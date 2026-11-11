package com.omnibank.service.agent.dd.service;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 尽调任务状态机：集中维护合法流转，禁止 Controller 或业务代码绕过校验直接改状态
 */
@Component
public class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS;

    static {
        Map<TaskStatus, Set<TaskStatus>> map = new EnumMap<>(TaskStatus.class);
        map.put(TaskStatus.DRAFT, EnumSet.of(TaskStatus.INITIALIZING, TaskStatus.CANCELLED));
        map.put(TaskStatus.INITIALIZING,
                EnumSet.of(TaskStatus.SCENARIO_IDENTIFYING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        map.put(TaskStatus.SCENARIO_IDENTIFYING,
                EnumSet.of(TaskStatus.PLANNING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        map.put(TaskStatus.PLANNING, EnumSet.of(TaskStatus.DATA_COLLECTING, TaskStatus.CANCELLED));
        map.put(TaskStatus.DATA_COLLECTING, EnumSet.of(TaskStatus.ANALYZING, TaskStatus.WAITING_FOR_SUPPLEMENT,
                TaskStatus.FAILED, TaskStatus.CANCELLED));
        map.put(TaskStatus.ANALYZING, EnumSet.of(TaskStatus.WAITING_FOR_SUPPLEMENT, TaskStatus.RISK_ASSESSING,
                TaskStatus.FAILED, TaskStatus.CANCELLED));
        map.put(TaskStatus.WAITING_FOR_SUPPLEMENT,
                EnumSet.of(TaskStatus.REANALYZING, TaskStatus.CANCELLED));
        map.put(TaskStatus.REANALYZING,
                EnumSet.of(TaskStatus.RISK_ASSESSING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        map.put(TaskStatus.RISK_ASSESSING, EnumSet.of(TaskStatus.REPORT_GENERATING, TaskStatus.FAILED,
                TaskStatus.CANCELLED));
        map.put(TaskStatus.REPORT_GENERATING, EnumSet.of(TaskStatus.WAITING_FOR_REVIEW, TaskStatus.FAILED,
                TaskStatus.CANCELLED));
        map.put(TaskStatus.WAITING_FOR_REVIEW,
                EnumSet.of(TaskStatus.COMPLETED, TaskStatus.RETURNED, TaskStatus.CANCELLED));
        map.put(TaskStatus.RETURNED,
                EnumSet.of(TaskStatus.REANALYZING, TaskStatus.REPORT_GENERATING, TaskStatus.CANCELLED));
        // 失败任务允许断点恢复：复用历史成功节点，重新执行失败与跳过节点
        map.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.ANALYZING));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * 校验流转合法性，非法流转抛业务异常
     */
    public void check(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> targets = TRANSITIONS.getOrDefault(from, Set.of());
        if (!targets.contains(to)) {
            throw new BizException(DdErrorCode.TASK_STATUS_NOT_ALLOWED,
                    "任务状态不允许从「" + from.getDesc() + "」流转为「" + to.getDesc() + "」");
        }
    }
}
