package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.ai.agent.JudicialRiskAgent;
import com.omnibank.service.agent.dd.ai.agent.JudicialRiskData;
import com.omnibank.service.agent.dd.ai.agent.JudicialRiskInput;
import com.omnibank.service.agent.dd.ai.agent.SiteVisitAgent;
import com.omnibank.service.agent.dd.ai.agent.SiteVisitData;
import com.omnibank.service.agent.dd.ai.agent.SiteVisitInput;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 司法风险与现场调查 Agent 集成测试：验证规则引擎输出的结构化结果、证据引用与 Mock 告警
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional
class DdJudicialSiteAgentIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 司法风险Agent返回结构化结果与证据并标记Mock() {
        long taskId = submittedTask();

        AgentResult<JudicialRiskData> result = agentRuntime.execute(JudicialRiskAgent.AGENT_CODE, taskId,
                new JudicialRiskInput(null, null));

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getAgentCode()).isEqualTo(JudicialRiskAgent.AGENT_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getLawsuitCount()).isGreaterThan(0);
        assertThat(result.getData().getLawsuits()).isNotEmpty();
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
        assertThat(result.getData().getPendingDefendantAmount()).isNotNull();
    }

    @Test
    void 现场调查Agent区分观察事实与访谈陈述并保留证据() {
        long taskId = submittedTask();

        AgentResult<SiteVisitData> result = agentRuntime.execute(SiteVisitAgent.AGENT_CODE, taskId,
                new SiteVisitInput(null, null));

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getAgentCode()).isEqualTo(SiteVisitAgent.AGENT_CODE);
        SiteVisitData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getEvidenceCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.getEvidenceRefs()).isNotEmpty();

        assertThat(data.getObservationNature()).isEqualTo("FACT");
        assertThat(data.getInvestigationDate()).isNotBlank();
        assertThat(data.getSiteAddress()).isNotBlank();
        assertThat(data.getProductionStatus()).isNotBlank();
        assertThat(data.getOnSiteStaffCount()).isNotNull();
        assertThat(data.getEquipmentOperatingRate()).isNotNull();
        assertThat(data.getInventoryObservation()).isNotBlank();
        assertThat(data.getObservationEvidenceId()).isNotBlank();

        assertThat(data.getStatementNature()).isEqualTo("STATEMENT");
        assertThat(data.getInterviewDate()).isNotBlank();
        assertThat(data.getCapacityUtilizationRate()).isNotNull();
        assertThat(data.getOrderBacklog()).isNotBlank();
        assertThat(data.getInterviewEmployeeCount()).isNotNull();
        assertThat(data.getMainCustomersStatement()).isNotBlank();
        assertThat(data.getMainSuppliersStatement()).isNotBlank();
        assertThat(data.getStatementEvidenceId()).isNotBlank();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
    }

    private long submittedTask() {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西主体核验测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APPAGT000001");
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
