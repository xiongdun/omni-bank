package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.context.UserContext;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.config.DdSseRegistry;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.dto.task.TaskDetailVO;
import com.omnibank.service.agent.dd.dto.task.TaskListVO;
import com.omnibank.service.agent.dd.dto.task.TaskQueryReq;
import com.omnibank.service.agent.dd.dto.task.TaskStatusVO;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.mapper.DdTaskMapper;
import com.omnibank.service.agent.dd.service.DdContextAssembler;
import com.omnibank.service.agent.dd.service.DdEnterpriseService;
import com.omnibank.service.agent.dd.service.DdLoanApplicationService;
import com.omnibank.service.agent.dd.service.DdOutboxService;
import com.omnibank.service.agent.dd.service.DdScenarioService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.service.TaskStateMachine;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import com.omnibank.service.agent.dd.support.DdViewConverter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 尽调任务服务实现：任务状态变更统一走状态机 + 乐观锁条件更新
 */
@Service
@RequiredArgsConstructor
public class DdTaskServiceImpl extends ServiceImpl<DdTaskMapper, DdTask> implements DdTaskService {

    private static final DateTimeFormatter TASK_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** Outbox 聚合类型 */
    private static final String AGGREGATE_TASK = "DD_TASK";

    /** 任务提交事件 */
    private static final String EVENT_TASK_SUBMITTED = "TASK_SUBMITTED";

    private final DdEnterpriseService enterpriseService;

    private final DdLoanApplicationService applicationService;

    private final DdScenarioService scenarioService;

    private final DdOutboxService outboxService;

    private final TaskStateMachine stateMachine;

    private final DdJsonSupport jsonSupport;

    private final DdViewConverter viewConverter;

    private final DdContextAssembler contextAssembler;

    private final DdSseRegistry sseRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO create(CreateTaskReq req) {
        var enterprise = enterpriseService.getOrCreate(req.getEnterprise());

        long taskId = IdWorker.getId();
        DdTask task = new DdTask();
        task.setId(taskId);
        task.setTaskNo("DD" + LocalDate.now().format(TASK_NO_DATE) + taskId);
        task.setEnterpriseId(enterprise.getId());
        task.setTaskName(StringUtils.hasText(req.getTaskName())
                ? req.getTaskName() : enterprise.getEnterpriseName() + "尽调任务");
        task.setTaskStatus(TaskStatus.DRAFT.name());
        task.setCurrentStage(TaskStatus.DRAFT.name());
        task.setContextVersion(1);
        task.setAnalysisVersion(0);
        task.setVersion(0);
        save(task);

        DdLoanApplication application = applicationService.create(taskId, req.getApplication(),
                jsonSupport.write(req.getApplication()));
        lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .set(DdTask::getApplicationId, application.getId())
                .update();

        TaskCreateVO vo = new TaskCreateVO();
        vo.setTaskId(taskId);
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskStatus(task.getTaskStatus());
        return vo;
    }

    @Override
    public TaskDetailVO detail(Long taskId) {
        DdTask task = requireTask(taskId);
        TaskDetailVO vo = new TaskDetailVO();
        BeanUtils.copyProperties(task, vo);
        vo.setTaskStatusDesc(parseStatus(task).getDesc());
        vo.setEnterprise(viewConverter.toEnterpriseVO(enterpriseService.getById(task.getEnterpriseId())));
        vo.setApplication(viewConverter.toApplicationVO(applicationService.findByTaskId(taskId)));
        vo.setLatestScenario(scenarioService.latestOrNull(taskId));
        return vo;
    }

    @Override
    public IPage<TaskListVO> page(TaskQueryReq req) {
        return lambdaQuery()
                .like(StringUtils.hasText(req.getTaskNo()), DdTask::getTaskNo, req.getTaskNo())
                .eq(StringUtils.hasText(req.getTaskStatus()), DdTask::getTaskStatus, req.getTaskStatus())
                .eq(req.getEnterpriseId() != null, DdTask::getEnterpriseId, req.getEnterpriseId())
                .orderByDesc(DdTask::getCreateTime)
                .page(new Page<>(req.getCurrent(), req.getSize()))
                .convert(this::toListVO);
    }

    @Override
    public TaskStatusVO status(Long taskId) {
        return toStatusVO(requireTask(taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskStatusVO submit(Long taskId) {
        DdTask task = requireTask(taskId);
        if (parseStatus(task) != TaskStatus.DRAFT) {
            throw new BizException(DdErrorCode.TASK_STATUS_NOT_ALLOWED, "仅草稿状态的任务可以提交");
        }
        applicationService.requireByTaskId(taskId);
        changeStatus(task, TaskStatus.INITIALIZING,
                wrapper -> wrapper.set(DdTask::getSubmittedAt, LocalDateTime.now()));
        outboxService.append(AGGREGATE_TASK, taskId, EVENT_TASK_SUBMITTED,
                Map.of("taskId", taskId, "taskNo", task.getTaskNo(),
                        "analysisVersion", task.getAnalysisVersion()));

        TaskStatusVO vo = toStatusVO(task);
        sseRegistry.publish(taskId, "task-status", vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long taskId) {
        DdTask task = requireTask(taskId);
        if (parseStatus(task).isTerminal()) {
            throw new BizException(DdErrorCode.TASK_NOT_CANCELLABLE);
        }
        changeStatus(task, TaskStatus.CANCELLED, null);
        sseRegistry.publish(taskId, "task-status", toStatusVO(task));
    }

    @Override
    public TaskContextVO context(Long taskId) {
        return contextAssembler.assemble(taskId);
    }

    @Override
    public DdTask requireTask(Long taskId) {
        DdTask task = getById(taskId);
        if (task == null) {
            throw new BizException(DdErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    @Override
    public boolean beginScenarioIdentify(Long taskId) {
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .eq(DdTask::getTaskStatus, TaskStatus.INITIALIZING.name())
                .set(DdTask::getTaskStatus, TaskStatus.SCENARIO_IDENTIFYING.name())
                .set(DdTask::getCurrentStage, TaskStatus.SCENARIO_IDENTIFYING.name())
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        return rows == 1;
    }

    @Override
    public void markPlanning(Long taskId) {
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .eq(DdTask::getTaskStatus, TaskStatus.SCENARIO_IDENTIFYING.name())
                .set(DdTask::getTaskStatus, TaskStatus.PLANNING.name())
                .set(DdTask::getCurrentStage, TaskStatus.PLANNING.name())
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BizException(DdErrorCode.TASK_STATUS_CONFLICT);
        }
        sseRegistry.publish(taskId, "task-status", status(taskId));
    }

    @Override
    public boolean beginDataCollecting(Long taskId) {
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .eq(DdTask::getTaskStatus, TaskStatus.PLANNING.name())
                .set(DdTask::getTaskStatus, TaskStatus.DATA_COLLECTING.name())
                .set(DdTask::getCurrentStage, TaskStatus.DATA_COLLECTING.name())
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            return false;
        }
        sseRegistry.publish(taskId, "task-status", status(taskId));
        return true;
    }

    @Override
    public void markAnalyzing(Long taskId) {
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .eq(DdTask::getTaskStatus, TaskStatus.DATA_COLLECTING.name())
                .set(DdTask::getTaskStatus, TaskStatus.ANALYZING.name())
                .set(DdTask::getCurrentStage, TaskStatus.ANALYZING.name())
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BizException(DdErrorCode.TASK_STATUS_CONFLICT);
        }
        sseRegistry.publish(taskId, "task-status", status(taskId));
    }

    @Override
    public boolean beginResume(Long taskId) {
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .eq(DdTask::getTaskStatus, TaskStatus.FAILED.name())
                .set(DdTask::getTaskStatus, TaskStatus.ANALYZING.name())
                .set(DdTask::getCurrentStage, TaskStatus.ANALYZING.name())
                .set(DdTask::getLastErrorCode, null)
                .set(DdTask::getLastErrorMessage, null)
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            return false;
        }
        sseRegistry.publish(taskId, "task-status", status(taskId));
        return true;
    }

    @Override
    public void updateCurrentPlan(Long taskId, Long planId) {
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .set(DdTask::getCurrentPlanId, planId)
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BizException(DdErrorCode.TASK_NOT_FOUND);
        }
    }

    @Override
    public void markFailed(Long taskId, String errorCode, String errorMessage) {
        String message = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000) : errorMessage;
        int rows = baseMapper.update(null, Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, taskId)
                .in(DdTask::getTaskStatus, TaskStatus.INITIALIZING.name(), TaskStatus.SCENARIO_IDENTIFYING.name(),
                        TaskStatus.PLANNING.name(), TaskStatus.DATA_COLLECTING.name(),
                        TaskStatus.ANALYZING.name())
                .set(DdTask::getTaskStatus, TaskStatus.FAILED.name())
                .set(DdTask::getCurrentStage, TaskStatus.FAILED.name())
                .set(DdTask::getLastErrorCode, errorCode)
                .set(DdTask::getLastErrorMessage, message)
                .set(DdTask::getUpdater, currentOperator())
                .setSql("version = version + 1"));
        if (rows == 1) {
            sseRegistry.publish(taskId, "task-status", status(taskId));
        }
    }

    /** 状态流转：状态机校验 + 版本号条件更新，冲突即失败 */
    private void changeStatus(DdTask task, TaskStatus target, Consumer<LambdaUpdateWrapper<DdTask>> extra) {
        stateMachine.check(parseStatus(task), target);
        LambdaUpdateWrapper<DdTask> wrapper = Wrappers.<DdTask>lambdaUpdate()
                .eq(DdTask::getId, task.getId())
                .eq(DdTask::getVersion, task.getVersion())
                .set(DdTask::getTaskStatus, target.name())
                .set(DdTask::getCurrentStage, target.name())
                .set(DdTask::getVersion, task.getVersion() + 1)
                .set(DdTask::getUpdater, currentOperator());
        if (extra != null) {
            extra.accept(wrapper);
        }
        if (baseMapper.update(null, wrapper) != 1) {
            throw new BizException(DdErrorCode.TASK_STATUS_CONFLICT);
        }
        task.setTaskStatus(target.name());
        task.setCurrentStage(target.name());
        task.setVersion(task.getVersion() + 1);
    }

    private TaskListVO toListVO(DdTask task) {
        TaskStatus status = parseStatus(task);
        TaskListVO vo = new TaskListVO();
        vo.setTaskId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskName(task.getTaskName());
        vo.setTaskStatus(status.name());
        vo.setTaskStatusDesc(status.getDesc());
        vo.setCurrentStage(task.getCurrentStage());
        vo.setProgress(status.getProgress());
        vo.setEnterpriseId(task.getEnterpriseId());
        vo.setApplicationId(task.getApplicationId());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private TaskStatusVO toStatusVO(DdTask task) {
        TaskStatus status = parseStatus(task);
        TaskStatusVO vo = new TaskStatusVO();
        vo.setTaskId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskStatus(status.name());
        vo.setTaskStatusDesc(status.getDesc());
        vo.setCurrentStage(task.getCurrentStage());
        vo.setContextVersion(task.getContextVersion());
        vo.setAnalysisVersion(task.getAnalysisVersion());
        vo.setProgress(status.getProgress());
        vo.setLastErrorCode(task.getLastErrorCode());
        vo.setLastErrorMessage(task.getLastErrorMessage());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private TaskStatus parseStatus(DdTask task) {
        return TaskStatus.valueOf(task.getTaskStatus());
    }

    private String currentOperator() {
        return StringUtils.hasText(UserContext.getUsername()) ? UserContext.getUsername() : "system";
    }
}
