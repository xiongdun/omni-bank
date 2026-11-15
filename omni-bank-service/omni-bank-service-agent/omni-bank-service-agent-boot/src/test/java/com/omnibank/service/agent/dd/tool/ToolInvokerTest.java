package com.omnibank.service.agent.dd.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.api.enums.DdErrorCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tool 统一调用层单元测试：参数校验、超时控制、异常隔离与结果透传。
 */
class ToolInvokerTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 调用成功透传工具结果() {
        ToolInvoker invoker = invoker(tool("tool.ok", true, 0L,
                request -> ToolResult.success("TEST", "ok", null, null)));

        ToolResult<String> result = invoker.invoke("tool.ok", new ToolRequest(1L, Map.of()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("ok");
        assertThat(invoker.definition("tool.ok").getToolCode()).isEqualTo("tool.ok");
    }

    @Test
    void 缺少taskId返回参数不合法() {
        ToolInvoker invoker = invoker(tool("tool.ok", true, 0L,
                request -> ToolResult.success("TEST", "ok", null, null)));

        ToolResult<String> result = invoker.invoke("tool.ok", new ToolRequest(null, Map.of()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(DdErrorCode.TOOL_PARAM_INVALID.getCode());
    }

    @Test
    void 真实工具超时返回超时错误() {
        ToolInvoker invoker = invoker(tool("tool.slow", false, 50L, request -> {
            sleep(1000L);
            return ToolResult.success("TEST", "late", null, null);
        }));

        ToolResult<String> result = invoker.invoke("tool.slow", new ToolRequest(1L, Map.of()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(DdErrorCode.TOOL_TIMEOUT.getCode());
    }

    @Test
    void 工具抛异常返回执行失败() {
        ToolInvoker invoker = invoker(tool("tool.error", true, 0L, request -> {
            throw new IllegalStateException("boom");
        }));

        ToolResult<String> result = invoker.invoke("tool.error", new ToolRequest(1L, Map.of()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(DdErrorCode.TOOL_EXECUTION_FAILED.getCode());
    }

    private ToolInvoker invoker(AgentTool<?> tool) {
        return new ToolInvoker(new ToolRegistry(List.of(tool)), executor);
    }

    private AgentTool<String> tool(String toolCode, boolean mock, long timeoutMs, ToolBody body) {
        ToolDefinition definition = ToolDefinition.of(toolCode, toolCode, "测试工具", "QUERY", mock, timeoutMs);
        return new AgentTool<>() {
            @Override
            public ToolDefinition getDefinition() {
                return definition;
            }

            @Override
            public ToolResult<String> execute(ToolRequest request) {
                return body.run(request);
            }
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface ToolBody {
        ToolResult<String> run(ToolRequest request);
    }
}
