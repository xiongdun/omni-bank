package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.ai.agent.DocumentRecognitionData;
import com.omnibank.service.agent.dd.ai.agent.IndustryExpertData;
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
 * P5 材料识别与行业专家 Agent 集成测试：验证规则引擎输出、Mock 标记与证据引用
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional
class DdDocumentIndustryAgentIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 材料识别Agent返回材料清单与完备度并标记Mock() {
        long taskId = submittedTask();

        AgentResult<DocumentRecognitionData> result = agentRuntime.execute("DOCUMENT_RECOGNITION", taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getAgentCode()).isEqualTo("DOCUMENT_RECOGNITION");
        assertThat(result.getData().getMaterials()).isNotEmpty();
        assertThat(result.getData().getMaterialCount()).isGreaterThan(0);
        assertThat(result.getData().getEvidenceCompleteness()).isNotNull();
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
        assertThat(result.getModelVersion()).isEqualTo("rule-engine");
    }

    @Test
    void 行业专家Agent返回行业分类与基准并引用行业证据() {
        long taskId = submittedTask();

        AgentResult<IndustryExpertData> result = agentRuntime.execute("INDUSTRY_EXPERT", taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getAgentCode()).isEqualTo("INDUSTRY_EXPERT");
        assertThat(result.getData().getIndustryCode()).isNotBlank();
        assertThat(result.getData().getIndustryName()).isNotBlank();
        assertThat(result.getData().getGrowthRates()).isNotEmpty();
        assertThat(result.getData().getGrossMarginMedian()).isNotNull();
        assertThat(result.getData().getAssetLiabilityRatioMedian()).isNotNull();
        assertThat(result.getData().getReceivableTurnoverDaysMedian()).isNotNull();
        assertThat(result.getEvidenceRefs()).isNotEmpty();
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
