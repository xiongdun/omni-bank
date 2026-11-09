package com.omnibank.service.agent.dd.controller;

import com.omnibank.common.core.page.PageResult;
import com.omnibank.common.core.result.Result;
import com.omnibank.common.mybatis.util.PageResults;
import com.omnibank.service.agent.dd.config.DdSseRegistry;
import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.dto.task.TaskDetailVO;
import com.omnibank.service.agent.dd.dto.task.TaskQueryReq;
import com.omnibank.service.agent.dd.dto.task.TaskStatusVO;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.service.DdScenarioService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 尽调任务接口：路径经网关 StripPrefix 后暴露为 /api/v1/dd/tasks
 */
@Slf4j
@RestController
@RequestMapping("/v1/dd/tasks")
@RequiredArgsConstructor
public class DdTaskController {

    private final DdTaskService taskService;

    private final DdScenarioService scenarioService;

    private final DdSseRegistry sseRegistry;

    /** 创建尽调任务（草稿） */
    @PostMapping
    public Result<TaskCreateVO> create(@Valid @RequestBody CreateTaskReq req) {
        return Result.success(taskService.create(req));
    }

    /** 任务分页 */
    @GetMapping
    public Result<PageResult<DdTask>> page(TaskQueryReq req) {
        return Result.success(PageResults.from(taskService.page(req)));
    }

    /** 任务详情 */
    @GetMapping("/{taskId}")
    public Result<TaskDetailVO> detail(@PathVariable Long taskId) {
        return Result.success(taskService.detail(taskId));
    }

    /** 任务状态 */
    @GetMapping("/{taskId}/status")
    public Result<TaskStatusVO> status(@PathVariable Long taskId) {
        return Result.success(taskService.status(taskId));
    }

    /** 任务状态实时推送：断线后调用状态接口恢复 */
    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long taskId) {
        SseEmitter emitter = sseRegistry.subscribe(taskId);
        try {
            emitter.send(SseEmitter.event().name("task-status").data(taskService.status(taskId)));
        } catch (IOException e) {
            log.debug("SSE 初始快照发送失败 taskId={}", taskId);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** 提交任务 */
    @PostMapping("/{taskId}/submit")
    public Result<TaskStatusVO> submit(@PathVariable Long taskId) {
        return Result.success(taskService.submit(taskId));
    }

    /** 取消任务 */
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId) {
        taskService.cancel(taskId);
        return Result.success();
    }

    /** 企业上下文 */
    @GetMapping("/{taskId}/context")
    public Result<TaskContextVO> context(@PathVariable Long taskId) {
        return Result.success(taskService.context(taskId));
    }

    /** 最新业务场景识别结果 */
    @GetMapping("/{taskId}/scenario")
    public Result<ScenarioVO> scenario(@PathVariable Long taskId) {
        return Result.success(scenarioService.latest(taskId));
    }

    /** 重新识别业务场景，生成新版本 */
    @PostMapping("/{taskId}/scenario/reidentify")
    public Result<ScenarioVO> reidentify(@PathVariable Long taskId) {
        return Result.success(scenarioService.reidentify(taskId));
    }
}
