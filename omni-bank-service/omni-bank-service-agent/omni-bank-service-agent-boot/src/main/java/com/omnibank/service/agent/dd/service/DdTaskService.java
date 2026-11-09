package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.dto.task.TaskDetailVO;
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
    IPage<DdTask> page(TaskQueryReq req);

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

    /** 场景识别完成，推进到生成尽调计划（P1 终点，P4 由 Graph 接管） */
    void markPlanning(Long taskId);

    /** 初始化失败，记录错误信息并置为失败 */
    void markFailed(Long taskId, String errorCode, String errorMessage);
}
