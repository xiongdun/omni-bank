package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.graph.GraphExecutor;
import com.omnibank.service.agent.dd.graph.GraphNodeStatus;
import com.omnibank.service.agent.dd.graph.GraphResult;
import com.omnibank.service.agent.dd.mapper.DdPlanMapper;
import com.omnibank.service.agent.dd.plan.DdAgentCatalog;
import com.omnibank.service.agent.dd.plan.DdPlanGraphFactory;
import com.omnibank.service.agent.dd.plan.DdPlanner;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * P4 集成测试：MOCK 模式计划生成与版本落库、计划转 Graph 调度、未交付 Agent 标记
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DdPlannerIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Autowired
    private DdPlanner planner;

    @Autowired
    private DdPlanGraphFactory graphFactory;

    @Autowired
    private GraphExecutor graphExecutor;

    @Autowired
    private DdPlanMapper planMapper;

    @Autowired
    private DdJsonSupport jsonSupport;

    @Test
    void Mock模式生成规则计划并按版本落库() {
        long taskId = submittedTask("APPPLAN" + System.nanoTime());

        DdTask task = taskService.requireTask(taskId);
        assertThat(task.getTaskStatus()).isEqualTo(TaskStatus.ANALYZING.name());
        assertThat(task.getCurrentPlanId()).isNotNull();
        assertThat(task.getLastErrorCode()).isNull();

        DdPlan plan = planMapper.selectById(task.getCurrentPlanId());
        assertThat(plan.getTaskId()).isEqualTo(taskId);
        assertThat(plan.getPlanVersion()).isEqualTo(1);
        assertThat(plan.getPlannerMode()).isEqualTo("MOCK");
        assertThat(plan.getModelName()).isEqualTo("MOCK_RULE");
        assertThat(plan.getBusinessScenario()).isNotBlank();

        List<String> agents = jsonSupport.readStringList(plan.getAgentPlan());
        assertThat(agents).hasSize(DdAgentCatalog.all().size())
                .contains(DdAgentCatalog.SUBJECT_VERIFICATION, DdAgentCatalog.DUE_DILIGENCE_REPORT);

        Map<String, List<String>> dependencies = jsonSupport.readStringListMap(plan.getDependencyPlan());
        assertThat(dependencies.get(DdAgentCatalog.FINANCIAL_ANALYSIS))
                .containsExactly(DdAgentCatalog.SUBJECT_VERIFICATION, DdAgentCatalog.DOCUMENT_RECOGNITION);
        assertThat(dependencies.get(DdAgentCatalog.DUE_DILIGENCE_REPORT)).hasSize(11);
    }

    @Test
    void Graph将未交付Agent标记为NOT_DELIVERED且整图继续() {
        long taskId = submittedTask("APPGRAPH" + System.nanoTime());
        DdTask task = taskService.requireTask(taskId);
        DdPlan plan = planMapper.selectById(task.getCurrentPlanId());

        GraphResult result = graphExecutor.execute(graphFactory.build(plan), taskId);

        List<String> delivered = List.of(DdAgentCatalog.SUBJECT_VERIFICATION,
                DdAgentCatalog.DOCUMENT_RECOGNITION, DdAgentCatalog.FINANCIAL_ANALYSIS,
                DdAgentCatalog.JUDICIAL_RISK, DdAgentCatalog.CREDIT_DEBT_ANALYSIS,
                DdAgentCatalog.SITE_VISIT, DdAgentCatalog.INDUSTRY_EXPERT,
                DdAgentCatalog.LOAN_PURPOSE_ANALYSIS, DdAgentCatalog.FRAUD_DETECTION,
                DdAgentCatalog.POLICY_MATCHING);
        assertThat(delivered).allSatisfy(code ->
                assertThat(result.getNodes().get(code).getStatus()).as("节点：%s", code)
                        .isEqualTo(GraphNodeStatus.SUCCEEDED));
        List.of(DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT, DdAgentCatalog.DUE_DILIGENCE_REPORT)
                .forEach(code -> assertThat(result.getNodes().get(code).getStatus()).as("节点：%s", code)
                        .isEqualTo(GraphNodeStatus.NOT_DELIVERED));
        assertThat(result.getNodes().get("join.0").getStatus()).isEqualTo(GraphNodeStatus.SUCCEEDED);
        assertThat(result.getNodes().get("join.0").getSummary()).contains("成功 2");
        assertThat(result.getStatus()).as("节点执行明细：%s", result.getNodes())
                .isEqualTo(com.omnibank.service.agent.dd.graph.GraphExecutionStatus.SUCCEEDED);
    }

    @Test
    void 重复生成计划版本递增并更新当前计划() {
        long taskId = submittedTask("APPRERUN" + System.nanoTime());

        DdPlan second = planner.plan(taskId);

        assertThat(second.getPlanVersion()).isEqualTo(2);
        assertThat(second.getPlannerMode()).isEqualTo("MOCK");
        assertThat(taskService.requireTask(taskId).getCurrentPlanId()).isEqualTo(second.getId());
    }

    private long submittedTask(String applicationNo) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西计划测试科技有限公司");

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
}
