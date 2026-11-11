package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.ai.agent.FraudDetectionData;
import com.omnibank.service.agent.dd.ai.agent.LoanPurposeAnalysisData;
import com.omnibank.service.agent.dd.ai.agent.PolicyMatchingData;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.plan.DdAgentCatalog;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * P6 集成测试：贷款用途、反欺诈、政策匹配三个专业 Agent 的规则输出、证据关联与边界约束
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional
class DdLoanPurposeFraudPolicyAgentIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 贷款用途Agent输出分类测算与交易背景且不输出最终审批金额() {
        long taskId = submittedTask();

        AgentResult<LoanPurposeAnalysisData> result = agentRuntime.execute(
                DdAgentCatalog.LOAN_PURPOSE_ANALYSIS, taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        LoanPurposeAnalysisData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getPurposeCategory()).isEqualTo("RAW_MATERIAL_PURCHASE");
        assertThat(data.getMeasuredDemand()).isNotNull();
        assertThat(data.getFundingGap()).isNotNull();
        assertThat(data.getDemandFormulaSource()).startsWith("MOCK_CONFIG:");
        assertThat(data.getAmountReasonableness()).isNotBlank();
        assertThat(data.getTermReasonableness()).isNotBlank();
        assertThat(data.getContractCount()).isNotNull();
        assertThat(data.getTransactionBackground()).isNotBlank();
        assertThat(data.getFundFlowClues()).isNotEmpty();
        assertThat(data.getPrimaryRepaymentAssessment()).isNotBlank();
        assertThat(data.isSecondaryCannotReplacePrimary()).isTrue();
        assertThat(data.getApprovalAmountNote()).contains("不输出最终审批金额");
        assertThat(data.getAnomalies()).isNotNull();
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
    }

    @Test
    void 反欺诈Agent输出规则编号版本与待核实线索且人工核查结果为空() {
        long taskId = submittedTask();

        AgentResult<FraudDetectionData> result = agentRuntime.execute(
                DdAgentCatalog.FRAUD_DETECTION, taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        FraudDetectionData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getRuleRecords()).isNotEmpty();
        assertThat(data.getRuleRecords()).allSatisfy(record -> {
            assertThat(record.getRuleCode()).isNotBlank();
            assertThat(record.getRuleVersion()).isNotBlank();
            assertThat(record.getManualVerificationResult()).isNull();
            assertThat(record.getStatus()).isEqualTo("NEEDS_VERIFICATION");
            assertThat(record.getEvidenceRefs()).isNotEmpty();
        });
        assertThat(data.getRuleHitCount()).isEqualTo(data.getRuleRecords().size());
        assertThat(data.getReasonableCauses()).isNotEmpty();
        assertThat(data.getPendingVerificationItems()).isNotEmpty();
        assertThat(data.getRiskImpact()).contains("不构成欺诈认定");
        assertThat(result.getFindings()).allSatisfy(finding ->
                assertThat(finding.getStatus()).isEqualTo("NEEDS_VERIFICATION"));
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
    }

    @Test
    void 政策匹配Agent输出可回溯条款与待人工确认项且不做合规结论() {
        long taskId = submittedTask();

        AgentResult<PolicyMatchingData> result = agentRuntime.execute(
                DdAgentCatalog.POLICY_MATCHING, taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        PolicyMatchingData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getMatchedPolicies()).isNotEmpty();
        assertThat(data.getClauseMatches()).isNotEmpty();
        assertThat(data.getClauseMatches()).allSatisfy(match -> {
            assertThat(match.getPolicyVersion()).isNotBlank();
            assertThat(match.getClauseNo()).isNotBlank();
            assertThat(match.getOriginalText()).isNotBlank();
            assertThat(match.getPageNo()).isNotBlank();
            assertThat(match.getSourceFile()).isNotBlank();
            assertThat(match.getFileHash()).isNotBlank();
            assertThat(match.getApplicability()).isNotBlank();
        });
        assertThat(data.getChecklist()).isNotEmpty();
        assertThat(data.getPendingManualConfirmations()).isNotEmpty();
        assertThat(data.getApplicabilityNote()).contains("不等于");
        assertThat(data.getComplianceConclusionNote()).contains("不输出合规结论");
        assertThat(data.getEvidenceCount()).isGreaterThan(1);
        assertThat(result.getEvidenceRefs()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
    }

    private long submittedTask() {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西政策匹配测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APPAGT000006");
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
