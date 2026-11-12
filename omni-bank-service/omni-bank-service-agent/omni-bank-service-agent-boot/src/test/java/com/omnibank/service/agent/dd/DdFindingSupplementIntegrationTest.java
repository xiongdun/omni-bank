package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceListVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceQueryReq;
import com.omnibank.service.agent.dd.dto.finding.FindingListVO;
import com.omnibank.service.agent.dd.dto.finding.FindingQueryReq;
import com.omnibank.service.agent.dd.dto.supplement.CompleteSupplementReq;
import com.omnibank.service.agent.dd.dto.supplement.CreateSupplementReq;
import com.omnibank.service.agent.dd.dto.supplement.SupplementQueryReq;
import com.omnibank.service.agent.dd.dto.supplement.SupplementRequestVO;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.entity.DdAgentExecution;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.mapper.DdAgentExecutionMapper;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.service.DdFindingService;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdPlanService;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.service.DdSupplementRequestService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * P9 集成测试：Agent 结果落库为线索与补充材料请求、完成补充后增量重分析、计划与证据溯源接口
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DdFindingSupplementIntegrationTest {

    private static final String SUCCESS = "000200";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Autowired
    private DdFindingService findingService;

    @Autowired
    private DdSupplementRequestService supplementRequestService;

    @Autowired
    private DdPlanService planService;

    @Autowired
    private DdReportService reportService;

    @Autowired
    private DdEvidenceService evidenceService;

    @Autowired
    private DdAgentExecutionMapper executionMapper;

    @Test
    void Agent结果落库为可检索线索与待补充请求() {
        long taskId = submittedTask("APPFIND" + System.nanoTime());

        IPage<FindingListVO> findings = findingService.page(taskId, new FindingQueryReq());
        assertThat(findings.getTotal()).isPositive();
        assertThat(findings.getRecords()).allSatisfy(finding -> {
            assertThat(finding.getAgentCode()).isNotBlank();
            assertThat(finding.getSeverity()).isNotBlank();
        });

        String severity = findings.getRecords().get(0).getSeverity();
        FindingQueryReq severityQuery = new FindingQueryReq();
        severityQuery.setSeverity(severity);
        assertThat(findingService.page(taskId, severityQuery).getRecords())
                .allMatch(finding -> severity.equals(finding.getSeverity()));

        IPage<SupplementRequestVO> supplements = supplementRequestService.page(taskId,
                new SupplementQueryReq());
        assertThat(supplements.getTotal()).isPositive();
        assertThat(supplements.getRecords()).allSatisfy(request ->
                assertThat(request.getDataCode()).isNotBlank());
    }

    @Test
    void 重复投递事件不会重复生成待补充请求() {
        long taskId = submittedTask("APPREQ" + System.nanoTime());
        long pending = supplementRequestService.countPending(taskId);
        assertThat(pending).isPositive();
        String statusBefore = taskService.status(taskId).getTaskStatus();

        outboxPublisher.publishPending();

        assertThat(supplementRequestService.countPending(taskId)).isEqualTo(pending);
        assertThat(taskService.status(taskId).getTaskStatus()).isEqualTo(statusBefore);
    }

    @Test
    void 完成补充请求触发增量重分析并生成新报告版本() {
        long taskId = submittedTask("APPREANALYZE" + System.nanoTime());
        DdDueDiligenceReport before = reportService.latest(taskId);
        assertThat(before).isNotNull();
        long executionsBefore = countExecutions(taskId);

        CreateSupplementReq createReq = new CreateSupplementReq();
        createReq.setDataCode("MANUAL-TEST-" + System.nanoTime());
        createReq.setDataName("手工补充测试材料");
        createReq.setReason("验证增量重分析");
        SupplementRequestVO created = supplementRequestService.create(taskId, createReq);

        CompleteSupplementReq completeReq = new CompleteSupplementReq();
        completeReq.setRemark("已补齐");
        SupplementRequestVO completed = supplementRequestService.complete(taskId, created.getRequestId(),
                completeReq);

        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(taskService.status(taskId).getTaskStatus())
                .isIn("WAITING_FOR_SUPPLEMENT", "WAITING_FOR_REVIEW");
        assertThat(countExecutions(taskId)).isGreaterThan(executionsBefore);

        DdDueDiligenceReport after = reportService.latest(taskId);
        assertThat(after).isNotNull();
        assertThat(after.getReportVersion()).isGreaterThan(before.getReportVersion());
    }

    @Test
    void 计划与证据溯源接口可查询() throws Exception {
        long taskId = submittedTask("APPTRACE" + System.nanoTime());
        long planId = planService.current(taskId).getId();

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/plans/current", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.planId").value(planId))
                .andExpect(jsonPath("$.data.agentPlan").isArray());

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/plans", taskId)
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/v1/dd/plans/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.taskId").value(taskId));

        IPage<EvidenceListVO> evidences = evidenceService.page(taskId, new EvidenceQueryReq());
        assertThat(evidences.getRecords()).isNotEmpty();
        long evidenceId = evidences.getRecords().get(0).getEvidenceId();

        mockMvc.perform(get("/v1/dd/evidences/{evidenceId}/references", evidenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.data.findings").isArray())
                .andExpect(jsonPath("$.data.reports").isArray())
                .andExpect(jsonPath("$.data.reviews").isArray());

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/findings", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/supplements", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS));
    }

    private long countExecutions(long taskId) {
        Long count = executionMapper.selectCount(Wrappers.<DdAgentExecution>lambdaQuery()
                .eq(DdAgentExecution::getTaskId, taskId));
        return count == null ? 0 : count;
    }

    private long submittedTask(String applicationNo) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode("91350100M000100Y43");
        enterprise.setEnterpriseName("江西线索测试科技有限公司");

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
