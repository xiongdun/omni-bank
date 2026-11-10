package com.omnibank.service.agent.dd.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 运行时：负责上下文构建、执行与结果兜底，Agent 自身只做单次分析。
 * 异步执行、Graph 编排、状态持久化与断点恢复由后续阶段实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final AgentRegistry agentRegistry;

    private final AgentContextBuilder contextBuilder;

    /**
     * 同步执行单个 Agent：未知 Agent 抛业务异常，Agent 内部异常转为 FAILED 结果而不是抛出。
     */
    public <I, O> AgentResult<O> execute(String agentCode, Long taskId, I input) {
        return execute(agentCode, taskId, input, Map.of());
    }

    /**
     * 同步执行单个 Agent，并注入上游节点结果（Graph 编排使用）。
     */
    @SuppressWarnings("unchecked")
    public <I, O> AgentResult<O> execute(String agentCode, Long taskId, I input,
                                         Map<String, Object> upstreamResults) {
        CreditAgent<I, O> agent = (CreditAgent<I, O>) agentRegistry.require(agentCode);
        AgentContext context = contextBuilder.build(taskId);
        context.setUpstreamResults(upstreamResults == null ? Map.of() : upstreamResults);
        AgentResult<O> result;
        try {
            result = agent.execute(context, input);
        } catch (Exception e) {
            log.error("Agent 执行失败 agentCode={}, taskId={}, executionId={}",
                    agentCode, taskId, context.getExecutionId(), e);
            result = new AgentResult<>();
            result.setStatus(AgentExecutionStatus.FAILED.name());
            result.setSummary("Agent 执行异常：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            result.setWarnings(List.of("执行异常，详见服务日志"));
        }
        if (result == null) {
            result = new AgentResult<>();
            result.setStatus(AgentExecutionStatus.FAILED.name());
            result.setSummary("Agent 未返回结果");
        }
        result.setAgentCode(agentCode);
        if (result.getExecutionId() == null) {
            result.setExecutionId(context.getExecutionId());
        }
        if (result.getStatus() == null) {
            result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
        }
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }
}
