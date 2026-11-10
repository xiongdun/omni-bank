package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceListVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceQueryReq;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * P2 证据中心集成测试：Mock 标记、采集幂等、过滤查询、证据详情与错误码
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@Transactional
class DdEvidenceIntegrationTest {

    private static final String SUCCESS = "000200";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdEvidenceService evidenceService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 任务初始化后采集三条Mock基础证据并写入上下文() {
        long taskId = submittedTask("91360000EVD000001", "APPEVD000001");

        IPage<EvidenceListVO> page = evidenceService.page(taskId, new EvidenceQueryReq());

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).allMatch(EvidenceListVO::getIsMock);
        assertThat(page.getRecords()).extracting(EvidenceListVO::getSourceSystem)
                .containsExactlyInAnyOrder("MOCK_ENTERPRISE_REGISTRY", "MOCK_JUDICIAL_PLATFORM", "MOCK_CREDIT_BUREAU");
        assertThat(page.getRecords()).allMatch(record -> record.getSourceType() != null);
        assertThat(taskService.context(taskId).getEvidenceIds()).hasSize(3);
        assertThat(taskService.status(taskId).getTaskStatus()).isEqualTo("ANALYZING");
    }

    @Test
    void 重复采集不产生重复证据() {
        long taskId = submittedTask("91360000EVD000002", "APPEVD000002");

        List<Long> first = evidenceService.collectBaseEvidence(taskId);
        List<Long> second = evidenceService.collectBaseEvidence(taskId);

        assertThat(first).hasSize(3);
        assertThat(second).containsExactlyElementsOf(first);
        assertThat(evidenceService.page(taskId, new EvidenceQueryReq()).getTotal()).isEqualTo(3);
    }

    @Test
    void 证据分页支持按类型与来源过滤() {
        long taskId = submittedTask("91360000EVD000003", "APPEVD000003");

        EvidenceQueryReq byType = new EvidenceQueryReq();
        byType.setEvidenceType("JUDICIAL_RECORD");
        IPage<EvidenceListVO> typePage = evidenceService.page(taskId, byType);
        assertThat(typePage.getTotal()).isEqualTo(1);
        assertThat(typePage.getRecords().get(0).getSourceType()).isEqualTo("EXTERNAL_DATA");

        EvidenceQueryReq bySourceType = new EvidenceQueryReq();
        bySourceType.setSourceType("BANK_INTERFACE");
        IPage<EvidenceListVO> sourceTypePage = evidenceService.page(taskId, bySourceType);
        assertThat(sourceTypePage.getTotal()).isEqualTo(1);
        assertThat(sourceTypePage.getRecords().get(0).getEvidenceType()).isEqualTo("CREDIT_REPORT");

        EvidenceQueryReq bySourceSystem = new EvidenceQueryReq();
        bySourceSystem.setSourceSystem("MOCK_ENTERPRISE_REGISTRY");
        assertThat(evidenceService.page(taskId, bySourceSystem).getTotal()).isEqualTo(1);
    }

    @Test
    void 证据详情返回溯源字段且不存在返回证据错误码() throws Exception {
        long taskId = submittedTask("91360000EVD000004", "APPEVD000004");
        Long evidenceId = evidenceService.page(taskId, new EvidenceQueryReq()).getRecords().get(0).getEvidenceId();

        mockMvc.perform(get("/v1/dd/evidences/{evidenceId}", evidenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.enterpriseId").isNumber())
                .andExpect(jsonPath("$.data.isMock").value(true))
                .andExpect(jsonPath("$.data.sourceReference").isString())
                .andExpect(jsonPath("$.data.contentHash").isString());

        mockMvc.perform(get("/v1/dd/evidences/{evidenceId}", 99999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("230115"));
    }

    @Test
    void 任务证据列表接口支持过滤且不暴露审计字段() throws Exception {
        long taskId = submittedTask("91360000EVD000005", "APPEVD000005");

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/evidences", taskId)
                        .param("evidenceType", "CREDIT_REPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].evidenceType").value("CREDIT_REPORT"))
                .andExpect(jsonPath("$.data.records[0].isMock").value(true))
                .andExpect(jsonPath("$.data.records[0].delFlag").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].creator").doesNotExist());
    }

    @Test
    void 查询不存在任务的证据返回任务不存在错误码() throws Exception {
        mockMvc.perform(get("/v1/dd/tasks/{taskId}/evidences", 99999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("230101"));
    }

    private long submittedTask(String creditCode, String applicationNo) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(creditCode);
        enterprise.setEnterpriseName("江西证据中心测试科技有限公司");

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
