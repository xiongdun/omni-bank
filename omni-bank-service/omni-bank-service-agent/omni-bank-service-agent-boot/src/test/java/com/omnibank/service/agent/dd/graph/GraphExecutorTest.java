package com.omnibank.service.agent.dd.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentRegistry;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.service.DdAgentExecutionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Graph 调度单元测试：不依赖 Spring 容器与数据库，覆盖定义校验、依赖传播与断点恢复复用。
 */
@ExtendWith(MockitoExtension.class)
class GraphExecutorTest {

    @Mock
    private AgentRuntime agentRuntime;

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private DdAgentExecutionService executionService;

    private GraphExecutor executor;

    @BeforeEach
    void setUp() {
        // 直接执行器避免测试引入线程池
        executor = new GraphExecutor(agentRuntime, agentRegistry, executionService, Runnable::run);
    }

    @Test
    void 缺少节点定义时拒绝执行() {
        GraphDefinition definition = definition("G_EMPTY");

        assertThatThrownBy(() -> executor.execute(definition, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未定义节点");
    }

    @Test
    void 节点依赖自身时拒绝执行() {
        GraphDefinition definition = definition("G_SELF");
        definition.setNodes(List.of(GraphNode.of("A", "AGENT_A", null, "A")));

        assertThatThrownBy(() -> executor.execute(definition, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能依赖自身");
    }

    @Test
    void 存在循环依赖时拒绝执行() {
        GraphDefinition definition = definition("G_CYCLE");
        definition.setNodes(List.of(
                GraphNode.of("A", "AGENT_A", null, "B"),
                GraphNode.of("B", "AGENT_B", null, "A")));

        assertThatThrownBy(() -> executor.execute(definition, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("循环依赖");
    }

    @Test
    void 未交付Agent标记为未执行且整体成功() {
        when(agentRegistry.contains("AGENT_A")).thenReturn(false);
        GraphDefinition definition = definition("G_NOT_DELIVERED");
        definition.setNodes(List.of(GraphNode.of("A", "AGENT_A", null)));

        GraphResult result = executor.execute(definition, 1L);

        assertThat(result.getNodes().get("A").getStatus()).isEqualTo(GraphNodeStatus.NOT_DELIVERED);
        assertThat(result.getNodes().get("A").getErrorMessage()).contains("未交付");
        assertThat(result.getStatus()).isEqualTo(GraphExecutionStatus.SUCCEEDED);
        verify(agentRuntime, never()).execute(anyString(), anyLong(), any(), anyMap());
        verify(executionService).record(anyLong(), any(), anyString(), any(), any());
    }

    @Test
    void 上游失败时下游跳过且整体失败() {
        when(agentRegistry.contains("AGENT_A")).thenReturn(true);
        doReturn(agentResult(AgentExecutionStatus.FAILED))
                .when(agentRuntime).execute(anyString(), anyLong(), any(), anyMap());
        GraphDefinition definition = definition("G_SKIP");
        definition.setNodes(List.of(
                GraphNode.of("A", "AGENT_A", null),
                GraphNode.of("B", "AGENT_B", null, "A")));

        GraphResult result = executor.execute(definition, 1L);

        assertThat(result.getNodes().get("A").getStatus()).isEqualTo(GraphNodeStatus.FAILED);
        assertThat(result.getNodes().get("B").getStatus()).isEqualTo(GraphNodeStatus.SKIPPED);
        assertThat(result.getNodes().get("B").getErrorMessage()).contains("A");
        // 无任何节点成功时整体判定为失败
        assertThat(result.getStatus()).isEqualTo(GraphExecutionStatus.FAILED);
        // 仅上游节点真正调用 Agent，下游被跳过
        verify(agentRuntime).execute(anyString(), anyLong(), any(), anyMap());
    }

    @Test
    void 汇聚节点放行下游并聚合上游结果() {
        when(agentRegistry.contains(anyString())).thenReturn(true);
        doReturn(agentResult(AgentExecutionStatus.SUCCEEDED))
                .when(agentRuntime).execute(anyString(), anyLong(), any(), anyMap());
        GraphDefinition definition = definition("G_JOIN");
        definition.setNodes(List.of(
                GraphNode.of("A", "AGENT_A", null),
                GraphNode.of("B", "AGENT_B", null),
                GraphNode.join("J", List.of("A", "B")),
                GraphNode.of("C", "AGENT_C", null, "J")));

        GraphResult result = executor.execute(definition, 1L);

        assertThat(result.getNodes().get("J").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);
        assertThat(result.getNodes().get("J").getSummary()).contains("成功 2");
        assertThat(result.getNodes().get("C").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);
        assertThat(result.getStatus()).isEqualTo(GraphExecutionStatus.SUCCEEDED);
        // 汇聚节点不写执行记录：3 个 Agent 节点各记录一次
        verify(executionService, times(3)).record(anyLong(), any(), anyString(),
                org.mockito.ArgumentMatchers.argThat(node -> !"J".equals(node.nodeId())), any());
    }

    @Test
    void 断点恢复复用历史成功结果且不重复记录() {
        when(executionService.loadSucceeded(1L, "G_RESUME"))
                .thenReturn(Map.of("A", succeededNode("A", "AGENT_A")));
        GraphDefinition definition = definition("G_RESUME");
        definition.setNodes(List.of(GraphNode.of("A", "AGENT_A", null)));

        GraphResult result = executor.execute(definition, 1L, 2L, Set.of());

        assertThat(result.getNodes().get("A").isReused()).isTrue();
        assertThat(result.getStatus()).isEqualTo(GraphExecutionStatus.SUCCEEDED);
        verify(agentRuntime, never()).execute(anyString(), anyLong(), any(), anyMap());
        verify(executionService, never()).record(anyLong(), any(), anyString(), any(), any());
    }

    private GraphDefinition definition(String graphCode) {
        GraphDefinition definition = new GraphDefinition();
        definition.setGraphCode(graphCode);
        definition.setName(graphCode);
        return definition;
    }

    private AgentResult<Map<String, Object>> agentResult(AgentExecutionStatus status) {
        AgentResult<Map<String, Object>> result = new AgentResult<>();
        result.setStatus(status.name());
        result.setSummary("mock " + status.name());
        result.setData(Map.of());
        return result;
    }

    private GraphNodeResult succeededNode(String nodeId, String agentCode) {
        GraphNodeResult nodeResult = new GraphNodeResult();
        nodeResult.setNodeId(nodeId);
        nodeResult.setAgentCode(agentCode);
        nodeResult.setStatus(GraphNodeStatus.SUCCEEDED);
        nodeResult.setSummary("历史成功");
        return nodeResult;
    }
}
