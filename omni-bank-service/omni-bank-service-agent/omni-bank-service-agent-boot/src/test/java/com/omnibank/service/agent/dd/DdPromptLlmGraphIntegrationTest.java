package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.ai.AgentContext;
import com.omnibank.service.agent.dd.ai.AgentDefinition;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.CreditAgent;
import com.omnibank.service.agent.dd.ai.agent.SubjectVerificationInput;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.graph.GraphDefinition;
import com.omnibank.service.agent.dd.graph.GraphExecutionStatus;
import com.omnibank.service.agent.dd.graph.GraphExecutor;
import com.omnibank.service.agent.dd.graph.GraphNode;
import com.omnibank.service.agent.dd.graph.GraphNodeStatus;
import com.omnibank.service.agent.dd.graph.GraphResult;
import com.omnibank.service.agent.dd.llm.DdChatService;
import com.omnibank.service.agent.dd.prompt.PromptManager;
import com.omnibank.service.agent.dd.prompt.PromptTemplate;
import com.omnibank.service.agent.dd.prompt.RenderedPrompt;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * P4 集成测试：Prompt 版本与渲染、Spring AI ChatClient（MOCK）、Spring AI Alibaba Graph 编排与失败隔离
 */
@SpringBootTest(classes = DdTestApplication.class)
@Import(DdPromptLlmGraphIntegrationTest.EchoAgentConfiguration.class)
@ActiveProfiles("h2")
@Transactional
class DdPromptLlmGraphIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private PromptManager promptManager;

    @Autowired
    private DdChatService chatService;

    @Autowired
    private GraphExecutor graphExecutor;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void Prompt模板可按版本加载并渲染变量() {
        assertThat(promptManager.versions("SUBJECT_VERIFICATION")).containsExactly("v1");

        PromptTemplate template = promptManager.require("SUBJECT_VERIFICATION", "1");
        assertThat(template.version()).isEqualTo("v1");
        assertThat(template.outputSchema()).contains("SubjectVerificationOutput");

        RenderedPrompt rendered = promptManager.render("SUBJECT_VERIFICATION", "v1", Map.of(
                "enterpriseName", "示例科技有限公司",
                "creditCode", VALID_CREDIT_CODE,
                "businessScenario", "WORKING_CAPITAL",
                "evidenceSummary", "- 工商登记证据（MOCK）"));
        assertThat(rendered.userPrompt()).contains("示例科技有限公司", VALID_CREDIT_CODE)
                .doesNotContain("{{");
        assertThat(rendered.systemPrompt()).contains("统一社会信用代码");
    }

    @Test
    void Prompt变量缺失与模板不存在返回业务错误码() {
        assertThatThrownBy(() -> promptManager.render("SUBJECT_VERIFICATION", "v1", Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230121"));

        assertThatThrownBy(() -> promptManager.require("NOT_EXISTS_AGENT", "v1"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230120"));
    }

    @Test
    void SpringAiChatClient在Mock模式下返回显式标记() {
        String content = chatService.complete("系统提示", "用户提示");

        assertThat(content).contains("\"mock\":true")
                .contains("\"model\":\"MOCK_CHAT_MODEL\"")
                .contains("MOCK_LLM_RESPONSE");
    }

    @Test
    void SpringAi结构化输出由框架完成解析与校验() {
        Map<String, Object> payload = chatService.completeForEntity("系统提示", "用户提示",
                new ParameterizedTypeReference<Map<String, Object>>() {
                });

        assertThat(payload).containsEntry("mock", true)
                .containsEntry("model", "MOCK_CHAT_MODEL");
    }

    @Test
    @SuppressWarnings("unchecked")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void Graph按依赖分层执行并向节点传递上游结果() {
        long taskId = submittedTask("APPAGT" + System.nanoTime());

        GraphDefinition definition = graph(
                GraphNode.of("subject", "SUBJECT_VERIFICATION", new SubjectVerificationInput(null, null)),
                GraphNode.of("echoA", UpstreamEchoAgent.AGENT_CODE, Map.of("branch", "A"), "subject"),
                GraphNode.of("echoB", UpstreamEchoAgent.AGENT_CODE, Map.of("branch", "B"), "subject"));

        GraphResult result = graphExecutor.execute(definition, taskId);

        assertThat(result.getStatus()).as("节点执行明细：%s", result.getNodes())
                .isEqualTo(GraphExecutionStatus.SUCCEEDED);
        assertThat(result.getNodes()).containsOnlyKeys("subject", "echoA", "echoB");
        assertThat(result.getNodes().values())
                .allMatch(node -> node.getStatus() == GraphNodeStatus.SUCCEEDED);
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);

        Map<String, Object> echoData = (Map<String, Object>) result.getNodes().get("echoA").getResult().getData();
        assertThat((List<String>) echoData.get("upstreamNodeIds")).containsExactly("subject");
        assertThat(echoData).containsEntry("input", Map.of("branch", "A"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void Graph未交付节点标记为未交付且下游跳过() {
        long taskId = submittedTask("APPAGT" + System.nanoTime());

        // Spring AI Alibaba Graph 的并行扇出要求同层分支汇聚到同一后继节点
        GraphDefinition definition = graph(
                GraphNode.of("root", "SUBJECT_VERIFICATION", new SubjectVerificationInput(null, null)),
                GraphNode.of("undelivered", "NOT_EXISTS_AGENT", Map.of(), "root"),
                GraphNode.of("healthy", UpstreamEchoAgent.AGENT_CODE, Map.of("branch", "B"), "root"),
                GraphNode.of("child", UpstreamEchoAgent.AGENT_CODE, Map.of(), "undelivered", "healthy"));

        GraphResult result = graphExecutor.execute(definition, taskId);

        assertThat(result.getStatus()).as("节点执行明细：%s", result.getNodes())
                .isEqualTo(GraphExecutionStatus.SUCCEEDED);
        assertThat(result.getNodes().get("root").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);
        assertThat(result.getNodes().get("undelivered").getStatus()).isEqualTo(GraphNodeStatus.NOT_DELIVERED);
        assertThat(result.getNodes().get("undelivered").getErrorMessage()).contains("未交付");
        assertThat(result.getNodes().get("healthy").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);
        assertThat(result.getNodes().get("child").getStatus()).isEqualTo(GraphNodeStatus.SKIPPED);
        assertThat(result.getNodes().get("child").getErrorMessage()).contains("undelivered");
        assertThat(result.getNodes().get("child").getResult()).isNull();
    }

    @Test
    void Graph非法定义返回业务错误码() {
        GraphDefinition cyclic = graph(
                GraphNode.of("a", "SUBJECT_VERIFICATION", null, "b"),
                GraphNode.of("b", "SUBJECT_VERIFICATION", null, "a"));
        assertThatThrownBy(() -> graphExecutor.execute(cyclic, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230122"));

        GraphDefinition empty = graph();
        assertThatThrownBy(() -> graphExecutor.execute(empty, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230122"));
    }

    private GraphDefinition graph(GraphNode... nodes) {
        GraphDefinition definition = new GraphDefinition();
        definition.setGraphCode("TEST_GRAPH");
        definition.setName("测试编排图");
        definition.setNodes(List.of(nodes));
        return definition;
    }

    private long submittedTask(String applicationNo) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西编排测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo(applicationNo);
        application.setProductCode("CORPORATE_WORKING_CAPITAL");
        application.setProductName("流动资金贷款");
        application.setApplyAmount(new BigDecimal("5000000.00"));
        application.setApplyTerm(12);
        application.setTermUnit("MONTH");
        application.setLoanPurpose("采购原材料");
        application.setGuaranteeType("MORTGAGE");

        CreateTaskReq req = new CreateTaskReq();
        req.setEnterprise(enterprise);
        req.setApplication(application);

        TaskCreateVO created = taskService.create(req);
        taskService.submit(created.getTaskId());
        outboxPublisher.publishPending();
        return created.getTaskId();
    }

    /** 测试用 Agent：回显上游节点ID与输入，用于验证 Graph 上下文传递 */
    static class UpstreamEchoAgent implements CreditAgent<Map<String, Object>, Map<String, Object>> {

        static final String AGENT_CODE = "TEST_UPSTREAM_ECHO";

        @Override
        public String getAgentCode() {
            return AGENT_CODE;
        }

        @Override
        public String getAgentName() {
            return "上游结果回显测试 Agent";
        }

        @Override
        public AgentDefinition getDefinition() {
            AgentDefinition definition = new AgentDefinition();
            definition.setAgentCode(AGENT_CODE);
            definition.setAgentName(getAgentName());
            definition.setDescription("仅用于测试，回显上游节点结果");
            definition.setVersion("1.0.0");
            definition.setEnabled(true);
            return definition;
        }

        @Override
        public AgentResult<Map<String, Object>> execute(AgentContext context, Map<String, Object> input) {
            AgentResult<Map<String, Object>> result = new AgentResult<>();
            result.setAgentCode(AGENT_CODE);
            result.setExecutionId(context.getExecutionId());
            result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
            result.setSummary("上游结果回显完成");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("upstreamNodeIds", List.copyOf(context.getUpstreamResults().keySet()));
            data.put("input", input);
            result.setData(data);
            return result;
        }
    }

    @TestConfiguration
    static class EchoAgentConfiguration {

        @Bean
        CreditAgent<Map<String, Object>, Map<String, Object>> upstreamEchoAgent() {
            return new UpstreamEchoAgent();
        }
    }
}
