package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.dto.task.TaskDetailVO;
import com.omnibank.service.agent.dd.dto.task.TaskListVO;
import com.omnibank.service.agent.dd.dto.task.TaskQueryReq;
import com.omnibank.service.agent.dd.dto.task.TaskStatusVO;
import com.omnibank.service.agent.dd.entity.DdTask;

/**
 * 尽调任务服务
 */
public interface DdTaskService {

    /** 创建草稿任务：复用或创建企业主体，保存申请快照 */
    TaskCreateVO create(CreateTaskReq req);

    /** 任务详情 */
    TaskDetailVO detail(Long taskId);

    /** 任务分页 */
    IPage<TaskListVO> page(TaskQueryReq req);

    /** 任务状态（轮询 / SSE 断线恢复） */
    TaskStatusVO status(Long taskId);

    /** 提交任务：草稿 -> 初始化，同事务写入 Outbox 事件 */
    TaskStatusVO submit(Long taskId);

    /** 取消任务 */
    void cancel(Long taskId);

    /** 组装企业上下文 */
    TaskContextVO context(Long taskId);

    /** 查询任务，不存在抛业务异常 */
    DdTask requireTask(Long taskId);

    /** 初始化阶段推进到场景识别，条件更新保证并发幂等；已被其他执行者推进时返回 false */
    boolean beginScenarioIdentify(Long taskId);

    /** 场景识别完成，推进到生成尽调计划（P4 由 Planner 生成计划并调度） */
    void markPlanning(Long taskId);

    /** 计划调度开始：PLANNING -> DATA_COLLECTING，条件更新保证并发幂等；状态不符返回 false */
    boolean beginDataCollecting(Long taskId);

    /** 计划调度完成：DATA_COLLECTING -> ANALYZING */
    void markAnalyzing(Long taskId);

    /** 失败任务恢复执行：FAILED -> ANALYZING，条件更新保证并发幂等；状态不符返回 false */
    boolean beginResume(Long taskId);

    /** 更新任务当前计划ID */
    void updateCurrentPlan(Long taskId, Long planId);

    /** 初始化失败，记录错误信息并置为失败 */
    void markFailed(Long taskId, String errorCode, String errorMessage);
}
