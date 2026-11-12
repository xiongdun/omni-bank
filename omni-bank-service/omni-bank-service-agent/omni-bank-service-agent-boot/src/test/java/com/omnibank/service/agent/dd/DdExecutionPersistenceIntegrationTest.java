package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.ai.AgentContext;
import com.omnibank.service.agent.dd.ai.AgentDefinition;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.CreditAgent;
import com.omnibank.service.agent.dd.dto.execution.AgentExecutionQueryReq;
import com.omnibank.service.agent.dd.dto.execution.AgentExecutionVO;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.entity.DdAgentExecution;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.graph.GraphDefinition;
import com.omnibank.service.agent.dd.graph.GraphExecutor;
import com.omnibank.service.agent.dd.graph.GraphNode;
import com.omnibank.service.agent.dd.graph.GraphNodeStatus;
import com.omnibank.service.agent.dd.graph.GraphResult;
import com.omnibank.service.agent.dd.mapper.DdAgentExecutionMapper;
import com.omnibank.service.agent.dd.plan.DdPlanScheduler;
import com.omnibank.service.agent.dd.service.DdAgentExecutionService;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 执行记录持久化与断点恢复集成测试：节点执行落库、重复执行序号递增、恢复复用成功节点、非失败任务拒绝恢复
 */
@SpringBootTest(classes = DdTestApplication.class)
@Import(DdExecutionPersistenceIntegrationTest.TestAgentConfiguration.class)
@ActiveProfiles("h2")
@AutoConfigureMockMvc
class DdExecutionPersistenceIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GraphExecutor graphExecutor;

    @Autowired
    private DdAgentExecutionService executionService;

    @Autowired
    private DdAgentExecutionMapper executionMapper;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdPlanScheduler planScheduler;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @BeforeEach
    void resetAgents() {
        CountingAgent.INVOCATIONS.set(0);
        FlakyAgent.INVOCATIONS.set(0);
        FlakyAgent.FAIL.set(false);
    }

    @Test
    void 任务链路执行后节点结果落库并可分页查询() {
        long taskId = submittedTask("APPEXEC" + System.nanoTime());

        AgentExecutionQueryReq req = new AgentExecutionQueryReq();
        req.setNodeId("SUBJECT_VERIFICATION");
        List<AgentExecutionVO> records = executionService.page(taskId, req).getRecords();

        assertThat(records).hasSize(1);
        AgentExecutionVO execution = records.get(0);
        assertThat(execution.getExecutionStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED.name());
        assertThat(execution.getPlanId()).isNotNull();
        assertThat(execution.getExecutionNo()).isEqualTo(1);
        assertThat(execution.getDurationMs()).isGreaterThanOrEqualTo(0);

        AgentExecutionQueryReq reportReq = new AgentExecutionQueryReq();
        reportReq.setNodeId("DUE_DILIGENCE_REPORT");
        assertThat(executionService.page(taskId, reportReq).getRecords())
                .singleElement()
                .satisfies(record -> assertThat(record.getExecutionStatus())
                        .isEqualTo(GraphNodeStatus.SUCCEEDED.name()));
    }

    @Test
    void Graph重复执行按节点序号递增记录() {
        long taskId = submittedTask("APPEXEC" + System.nanoTime());
        GraphDefinition definition = graph("TEST_COUNT_GRAPH",
                GraphNode.of("count", CountingAgent.AGENT_CODE, Map.of("round", 1)));

        graphExecutor.execute(definition, taskId, null, false);
        graphExecutor.execute(definition, taskId, null, false);
        GraphResult resumed = graphExecutor.execute(definition, taskId, null, true);

        assertThat(CountingAgent.INVOCATIONS).hasValue(2);
        assertThat(resumed.getNodes().get("count").isReused()).isTrue();
        assertThat(resumed.getNodes().get("count").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);

        List<DdAgentExecution> records = executionMapper.selectList(Wrappers.<DdAgentExecution>lambdaQuery()
                .eq(DdAgentExecution::getTaskId, taskId)
                .eq(DdAgentExecution::getGraphCode, "TEST_COUNT_GRAPH")
                .eq(DdAgentExecution::getNodeId, "count")
                .orderByAsc(DdAgentExecution::getExecutionNo));
        assertThat(records).hasSize(2);
        assertThat(records).extracting(DdAgentExecution::getExecutionNo).containsExactly(1, 2);
        assertThat(records).extracting(DdAgentExecution::getExecutionStatus)
                .containsOnly(GraphNodeStatus.SUCCEEDED.name());
    }

    @Test
    void 断点恢复复用成功节点并重跑失败节点() {
        long taskId = submittedTask("APPEXEC" + System.nanoTime());
        GraphDefinition initial = graph("TEST_RESUME_GRAPH",
                GraphNode.of("count", CountingAgent.AGENT_CODE, Map.of("round", 1)));
        graphExecutor.execute(initial, taskId, null, false);

        FlakyAgent.FAIL.set(true);
        GraphDefinition definition = graph("TEST_RESUME_GRAPH",
                GraphNode.of("count", CountingAgent.AGENT_CODE, Map.of("round", 2)),
                GraphNode.of("flaky", FlakyAgent.AGENT_CODE, Map.of(), "count"));
        GraphResult failed = graphExecutor.execute(definition, taskId, null, false);
        assertThat(failed.getNodes().get("flaky").getStatus()).isEqualTo(GraphNodeStatus.FAILED);
        assertThat(CountingAgent.INVOCATIONS).hasValue(2);

        FlakyAgent.FAIL.set(false);
        GraphResult resumed = graphExecutor.execute(definition, taskId, null, true);

        assertThat(resumed.getNodes().get("count").isReused()).isTrue();
        assertThat(resumed.getNodes().get("flaky").isReused()).isFalse();
        assertThat(resumed.getNodes().get("flaky").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);
        assertThat(CountingAgent.INVOCATIONS).as("恢复执行不应重跑已成功节点").hasValue(2);
        assertThat(FlakyAgent.INVOCATIONS).hasValue(2);

        List<DdAgentExecution> flakyRecords = executionMapper.selectList(Wrappers.<DdAgentExecution>lambdaQuery()
                .eq(DdAgentExecution::getTaskId, taskId)
                .eq(DdAgentExecution::getGraphCode, "TEST_RESUME_GRAPH")
                .eq(DdAgentExecution::getNodeId, "flaky")
                .orderByAsc(DdAgentExecution::getExecutionNo));
        assertThat(flakyRecords).extracting(DdAgentExecution::getExecutionStatus)
                .containsExactly(GraphNodeStatus.FAILED.name(), GraphNodeStatus.SUCCEEDED.name());
    }

    @Test
    void 非失败任务恢复执行返回业务错误码() {
        long draftTaskId = createTask("APPEXEC" + System.nanoTime());
        assertThatThrownBy(() -> planScheduler.resume(draftTaskId))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230128"));

        long analyzingTaskId = submittedTask("APPEXEC" + System.nanoTime());
        assertThat(taskService.status(analyzingTaskId).getTaskStatus()).isEqualTo(TaskStatus.ANALYZING.name());
        assertThatThrownBy(() -> planScheduler.resume(analyzingTaskId))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230128"));
    }

    @Test
    void 执行记录与恢复接口路径映射正确() throws Exception {
        long taskId = submittedTask("APPEXEC" + System.nanoTime());
        mockMvc.perform(get("/v1/dd/tasks/{taskId}/executions", taskId)
                        .param("nodeId", "SUBJECT_VERIFICATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000200"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].executionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.records[0].resultContent").doesNotExist());

        long draftTaskId = createTask("APPEXEC" + System.nanoTime());
        mockMvc.perform(post("/v1/dd/tasks/{taskId}/resume", draftTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("230128"));
    }

    private GraphDefinition graph(String graphCode, GraphNode... nodes) {
        GraphDefinition definition = new GraphDefinition();
        definition.setGraphCode(graphCode);
        definition.setName("执行记录测试图");
        definition.setNodes(List.of(nodes));
        return definition;
    }

    private long createTask(String applicationNo) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西执行记录测试科技有限公司");

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
        return created.getTaskId();
    }

    private long submittedTask(String applicationNo) {
        long taskId = createTask(applicationNo);
        taskService.submit(taskId);
        outboxPublisher.publishPending();
        return taskId;
    }

    /** 计数 Agent：记录被调用次数，用于校验断点恢复是否复用历史成功节点 */
    static class CountingAgent implements CreditAgent<Map<String, Object>, Map<String, Object>> {

        static final String AGENT_CODE = "TEST_COUNT_AGENT";

        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public String getAgentCode() {
            return AGENT_CODE;
        }

        @Override
        public String getAgentName() {
            return "执行计数测试 Agent";
        }

        @Override
        public AgentDefinition getDefinition() {
            return definition(AGENT_CODE, getAgentName(), "仅用于测试，统计执行次数");
        }

        @Override
        public AgentResult<Map<String, Object>> execute(AgentContext context, Map<String, Object> input) {
            AgentResult<Map<String, Object>> result = new AgentResult<>();
            result.setAgentCode(AGENT_CODE);
            result.setExecutionId(context.getExecutionId());
            result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
            result.setSummary("计数 Agent 第 " + INVOCATIONS.incrementAndGet() + " 次执行");
            result.setData(Map.of("input", input == null ? Map.of() : input));
            return result;
        }
    }

    /** 可失败 Agent：FAIL 为 true 时返回失败结果，用于验证恢复时重新执行失败节点 */
    static class FlakyAgent implements CreditAgent<Map<String, Object>, Map<String, Object>> {

        static final String AGENT_CODE = "TEST_FLAKY_AGENT";

        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        static final AtomicBoolean FAIL = new AtomicBoolean();

        @Override
        public String getAgentCode() {
            return AGENT_CODE;
        }

        @Override
        public String getAgentName() {
            return "可失败测试 Agent";
        }

        @Override
        public AgentDefinition getDefinition() {
            return definition(AGENT_CODE, getAgentName(), "仅用于测试，可模拟失败");
        }

        @Override
        public AgentResult<Map<String, Object>> execute(AgentContext context, Map<String, Object> input) {
            AgentResult<Map<String, Object>> result = new AgentResult<>();
            result.setAgentCode(AGENT_CODE);
            result.setExecutionId(context.getExecutionId());
            INVOCATIONS.incrementAndGet();
            boolean fail = FAIL.get();
            result.setStatus(fail ? AgentExecutionStatus.FAILED.name() : AgentExecutionStatus.SUCCEEDED.name());
            result.setSummary(fail ? "模拟执行失败" : "模拟执行成功");
            result.setData(Map.of("failed", fail));
            return result;
        }
    }

    @TestConfiguration
    static class TestAgentConfiguration {

        @Bean
        CreditAgent<Map<String, Object>, Map<String, Object>> countingAgent() {
            return new CountingAgent();
        }

        @Bean
        CreditAgent<Map<String, Object>, Map<String, Object>> flakyAgent() {
            return new FlakyAgent();
        }
    }

    private static AgentDefinition definition(String agentCode, String agentName, String description) {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode(agentCode);
        definition.setAgentName(agentName);
        definition.setDescription(description);
        definition.setVersion("1.0.0");
        definition.setEnabled(true);
        return definition;
    }
}
