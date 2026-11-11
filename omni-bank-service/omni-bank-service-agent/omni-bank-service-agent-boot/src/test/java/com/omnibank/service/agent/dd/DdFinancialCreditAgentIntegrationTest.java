package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.ai.agent.CreditDebtAnalysisData;
import com.omnibank.service.agent.dd.ai.agent.FinancialAnalysisData;
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
 * 财务分析 Agent 与征信债务 Agent 集成测试：验证规则计算、证据关联与 Mock 告警
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional
class DdFinancialCreditAgentIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 财务分析Agent返回多期指标与勾稽结果并标记Mock() {
        long taskId = submittedTask();

        AgentResult<FinancialAnalysisData> result = agentRuntime.execute("FINANCIAL_ANALYSIS", taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        FinancialAnalysisData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getStatementPeriods()).isGreaterThanOrEqualTo(2);
        assertThat(data.getPeriodIndicators()).isNotEmpty();
        assertThat(data.getPeriodIndicators()).allSatisfy(indicator -> {
            assertThat(indicator.getRevenue()).isNotNull();
            assertThat(indicator.getCurrentRatio()).isNotNull();
            assertThat(indicator.getDebtAssetRatio()).isNotNull();
            assertThat(indicator.getGrossMargin()).isNotNull();
        });
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
        assertThat(data.getReconciliations()).isNotEmpty();
        assertThat(data.getRiskFlags()).isNotNull();
    }

    @Test
    void 征信债务Agent返回使用率与逾期信息并关联证据() {
        long taskId = submittedTask();

        AgentResult<CreditDebtAnalysisData> result = agentRuntime.execute("CREDIT_DEBT_ANALYSIS", taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        CreditDebtAnalysisData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getCreditUtilization()).isNotNull();
        assertThat(data.getOverdueCount()).isNotNull();
        assertThat(data.getOverdueAmount()).isNotNull();
        assertThat(data.getTotalInterestBearingDebt()).isNotNull();
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
    }

    private long submittedTask() {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西财务征信测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APPAGT000002");
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
