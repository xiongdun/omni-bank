package com.omnibank.service.agent.dd.tool;

import com.omnibank.service.agent.api.enums.DdErrorCode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Tool 统一调用层：权限边界、参数校验、超时控制、异常隔离与调用审计
 */
@Component
public class ToolInvoker {

    private static final Logger log = LoggerFactory.getLogger(ToolInvoker.class);

    private final ToolRegistry registry;

    private final ToolPermissionRegistry permissions;

    private final Executor executor;

    public ToolInvoker(ToolRegistry registry, ToolPermissionRegistry permissions,
                       @Qualifier("ttlExecutor") Executor executor) {
        this.registry = registry;
        this.permissions = permissions;
        this.executor = executor;
    }

    public ToolDefinition definition(String toolCode) {
        return registry.require(toolCode).getDefinition();
    }

    public <T> ToolResult<T> invoke(String agentCode, String toolCode, ToolRequest request) {
        AgentTool<T> tool = registry.require(toolCode);
        ToolDefinition definition = tool.getDefinition();
        long startNanos = System.nanoTime();
        if (!permissions.allowed(agentCode, toolCode)) {
            return audit(agentCode, toolCode, definition, request, ToolResult.failure(toolCode,
                    DdErrorCode.TOOL_PERMISSION_DENIED.getCode(),
                    "Agent【" + agentCode + "】无权调用工具：" + toolCode), startNanos);
        }
        if (request == null || request.taskId() == null) {
            return audit(agentCode, toolCode, definition, request, ToolResult.failure(toolCode,
                    DdErrorCode.TOOL_PARAM_INVALID.getCode(), "工具调用缺少 taskId，无法归属任务上下文"), startNanos);
        }
        ToolResult<T> result;
        try {
            result = execute(tool, definition, request);
        } catch (TimeoutException e) {
            result = ToolResult.failure(toolCode, DdErrorCode.TOOL_TIMEOUT.getCode(),
                    "工具调用超时：" + toolCode + " 超过 " + definition.getTimeoutMs() + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = ToolResult.failure(toolCode, DdErrorCode.TOOL_EXECUTION_FAILED.getCode(),
                    "工具调用被中断：" + toolCode);
        } catch (RuntimeException e) {
            log.error("dd.tool.error agentCode={} toolCode={} taskId={}", agentCode, toolCode, request.taskId(), e);
            result = ToolResult.failure(toolCode, DdErrorCode.TOOL_EXECUTION_FAILED.getCode(),
                    "工具调用执行失败：" + toolCode);
        }
        return audit(agentCode, toolCode, definition, request, result, startNanos);
    }

    /** Mock 与本地规则工具同步执行以复用调用方事务；声明超时的真实工具走线程池，超时后中断并返回失败 */
    private <T> ToolResult<T> execute(AgentTool<T> tool, ToolDefinition definition, ToolRequest request)
            throws TimeoutException, InterruptedException {
        if (definition.isMock() || definition.getTimeoutMs() <= 0) {
            return tool.execute(request);
        }
        Future<ToolResult<T>> future = CompletableFuture.supplyAsync(() -> tool.execute(request), executor);
        try {
            return future.get(definition.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private <T> ToolResult<T> audit(String agentCode, String toolCode, ToolDefinition definition,
                                    ToolRequest request, ToolResult<T> result, long startNanos) {
        long costMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (result.isSuccess()) {
            log.info("dd.tool.call agentCode={} toolCode={} taskId={} mock={} status={} costMs={} evidenceCount={}",
                    agentCode, toolCode, request == null ? null : request.taskId(), definition.isMock(),
                    result.getStatus(), costMs, result.getEvidenceIds().size());
        } else {
            log.warn("dd.tool.call agentCode={} toolCode={} taskId={} mock={} status={} costMs={} errorCode={}",
                    agentCode, toolCode, request == null ? null : request.taskId(), definition.isMock(),
                    result.getStatus(), costMs, result.getErrorCode());
        }
        return result;
    }
}
