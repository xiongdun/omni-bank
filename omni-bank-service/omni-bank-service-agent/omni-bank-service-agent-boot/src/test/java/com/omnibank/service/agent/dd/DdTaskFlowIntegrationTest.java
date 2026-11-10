package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.dto.task.TaskDetailVO;
import com.omnibank.service.agent.dd.dto.task.TaskListVO;
import com.omnibank.service.agent.dd.dto.task.TaskQueryReq;
import com.omnibank.service.agent.dd.dto.task.TaskStatusVO;
import com.omnibank.service.agent.dd.entity.DdOutboxEvent;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.mapper.DdTaskMapper;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdOutboxService;
import com.omnibank.service.agent.dd.service.DdScenarioService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块一主链路集成测试：真实 SQL（H2 MySQL 模式）、事务、Outbox、场景识别、上下文组装
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@Transactional
class DdTaskFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdScenarioService scenarioService;

    @Autowired
    private DdOutboxService outboxService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Autowired
    private DdTaskMapper taskMapper;

    @Test
    void 创建任务时复用企业主体并保存申请快照() {
        TaskCreateVO first = taskService.create(req("91360000TEST000001", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));
        TaskDetailVO detail = taskService.detail(first.getTaskId());

        assertThat(first.getTaskNo()).startsWith("DD");
        assertThat(detail.getTaskStatus()).isEqualTo("DRAFT");
        assertThat(detail.getEnterprise().getCreditCode()).isEqualTo("91360000TEST000001");
        assertThat(detail.getApplication().getApplyAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(detail.getLatestScenario()).isNull();

        TaskCreateVO second = taskService.create(req("91360000TEST000001", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));
        assertThat(taskService.detail(second.getTaskId()).getEnterprise().getId())
                .isEqualTo(detail.getEnterprise().getId());
    }

    @Test
    void 提交任务后Outbox事件驱动场景识别计划生成并进入分析阶段() {
        TaskCreateVO created = taskService.create(req("91360000TEST000002", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));

        TaskStatusVO submitted = taskService.submit(created.getTaskId());
        assertThat(submitted.getTaskStatus()).isEqualTo("INITIALIZING");
        assertThatThrownBy(() -> taskService.submit(created.getTaskId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅草稿状态");

        List<DdOutboxEvent> pending = outboxService.fetchPending(10);
        assertThat(pending).anyMatch(e -> e.getAggregateId().equals(created.getTaskId()));

        outboxPublisher.publishPending();

        TaskStatusVO status = taskService.status(created.getTaskId());
        assertThat(status.getTaskStatus()).isEqualTo("ANALYZING");
        assertThat(status.getProgress()).isEqualTo(60);

        ScenarioVO scenario = scenarioService.latest(created.getTaskId());
        assertThat(scenario.getScenarioVersion()).isEqualTo(1);
        assertThat(scenario.getBusinessType()).isEqualTo("WORKING_CAPITAL");
        assertThat(scenario.getIdentifyStatus()).isEqualTo("IDENTIFIED");
        assertThat(scenario.getConfidence()).isEqualByComparingTo(new BigDecimal("0.90"));
        assertThat(scenario.getGuaranteeTypes()).containsExactly("MORTGAGE");

        TaskContextVO context = taskService.context(created.getTaskId());
        assertThat(context.getCreditCode()).isEqualTo("91360000TEST000002");
        assertThat(context.getLoanApplication().getLoanPurpose()).isEqualTo("采购原材料");
        assertThat(context.getBusinessScenario().getBusinessType()).isEqualTo("WORKING_CAPITAL");
        assertThat(context.getContextVersion()).isEqualTo(1);
        assertThat(context.getEvidenceIds()).hasSize(3);
    }

    @Test
    void 重复投递同一事件不会重复分析() {
        TaskCreateVO created = taskService.create(req("91360000TEST000003", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));
        taskService.submit(created.getTaskId());
        outboxPublisher.publishPending();

        DdTask afterFirst = taskMapper.selectById(created.getTaskId());
        int analysisVersion = afterFirst.getAnalysisVersion();

        outboxPublisher.publishPending();
        DdTask afterSecond = taskMapper.selectById(created.getTaskId());

        assertThat(afterSecond.getTaskStatus()).isEqualTo("ANALYZING");
        assertThat(afterSecond.getAnalysisVersion()).isEqualTo(analysisVersion);
        assertThat(afterSecond.getVersion()).isEqualTo(afterFirst.getVersion());
    }

    @Test
    void 规则未命中时场景标记待确认() {
        TaskCreateVO created = taskService.create(req("91360000TEST000004", "江西测试科技有限公司",
                "UNKNOWN_PRODUCT", "补充日常经营周转", "MORTGAGE"));
        taskService.submit(created.getTaskId());
        outboxPublisher.publishPending();

        ScenarioVO scenario = scenarioService.latest(created.getTaskId());
        assertThat(scenario.getBusinessType()).isEqualTo("OTHER");
        assertThat(scenario.getIdentifyStatus()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(scenario.getUncertainItems()).isNotEmpty();
        assertThat(taskService.status(created.getTaskId()).getTaskStatus()).isEqualTo("ANALYZING");
    }

    @Test
    void 取消后不可再提交() {
        TaskCreateVO created = taskService.create(req("91360000TEST000005", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));

        taskService.cancel(created.getTaskId());
        assertThat(taskService.status(created.getTaskId()).getTaskStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> taskService.cancel(created.getTaskId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可取消");
        assertThatThrownBy(() -> taskService.submit(created.getTaskId()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 任务详情与分页可按状态过滤() {
        TaskCreateVO created = taskService.create(req("91360000TEST000006", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));

        TaskDetailVO detail = taskService.detail(created.getTaskId());
        assertThat(detail.getTaskNo()).isEqualTo(created.getTaskNo());
        assertThat(detail.getTaskName()).isEqualTo("江西测试科技有限公司尽调任务");
        assertThat(detail.getTaskStatusDesc()).isEqualTo("草稿");
        assertThat(detail.getEnterprise().getEnterpriseName()).isEqualTo("江西测试科技有限公司");
        assertThat(detail.getApplication().getApplicationNo()).isEqualTo("APP000006");
        assertThat(detail.getLatestScenario()).isNull();

        TaskQueryReq query = new TaskQueryReq();
        query.setTaskNo(created.getTaskNo());
        query.setTaskStatus("DRAFT");
        IPage<TaskListVO> page = taskService.page(query);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(TaskListVO::getTaskId).containsExactly(created.getTaskId());
    }

    @Test
    void 重新识别场景生成递增版本() {
        TaskCreateVO created = taskService.create(req("91360000TEST000007", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));
        taskService.submit(created.getTaskId());
        outboxPublisher.publishPending();

        ScenarioVO first = scenarioService.latest(created.getTaskId());
        ScenarioVO second = scenarioService.reidentify(created.getTaskId());

        assertThat(second.getScenarioVersion()).isEqualTo(first.getScenarioVersion() + 1);
        assertThat(second.getBusinessType()).isEqualTo(first.getBusinessType());
        assertThat(second.getIdentifyStatus()).isEqualTo("IDENTIFIED");
        assertThat(scenarioService.latest(created.getTaskId()).getScenarioVersion())
                .isEqualTo(second.getScenarioVersion());
    }

    @Test
    void SSE端点先推送任务状态快照() throws Exception {
        TaskCreateVO created = taskService.create(req("91360000TEST000008", "江西测试科技有限公司",
                "CORPORATE_WORKING_CAPITAL", "采购原材料", "MORTGAGE"));

        MvcResult result = mockMvc.perform(get("/v1/dd/tasks/{taskId}/events", created.getTaskId()))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("task-status").contains("DRAFT");
    }

    private CreateTaskReq req(String creditCode, String enterpriseName, String productCode,
                              String loanPurpose, String guaranteeType) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(creditCode);
        enterprise.setEnterpriseName(enterpriseName);

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APP" + creditCode.substring(creditCode.length() - 6));
        application.setProductCode(productCode);
        application.setProductName("流动资金贷款");
        application.setApplyAmount(new BigDecimal("5000000.00"));
        application.setApplyTerm(12);
        application.setTermUnit("MONTH");
        application.setLoanPurpose(loanPurpose);
        application.setGuaranteeType(guaranteeType);

        CreateTaskReq req = new CreateTaskReq();
        req.setEnterprise(enterprise);
        req.setApplication(application);
        return req;
    }
}
