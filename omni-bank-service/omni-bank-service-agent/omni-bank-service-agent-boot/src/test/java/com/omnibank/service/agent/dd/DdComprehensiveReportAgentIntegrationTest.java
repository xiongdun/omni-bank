package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.ai.agent.ComprehensiveRiskAssessmentData;
import com.omnibank.service.agent.dd.ai.agent.DueDiligenceReportData;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.plan.DdAgentCatalog;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * P7 集成测试：综合风险与尽调报告 Agent 的评估条件、边界约束、报告章节与版本落库
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional
class DdComprehensiveReportAgentIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Autowired
    private DdReportService reportService;

    @Test
    void 综合风险Agent在缺少关键上游时明确暂不具备完整评估条件() {
        long taskId = submittedTask();

        AgentResult<ComprehensiveRiskAssessmentData> result = agentRuntime.execute(
                DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT, taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        ComprehensiveRiskAssessmentData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.isAssessable()).isFalse();
        assertThat(data.getNotAssessableReasons()).hasSize(3);
        assertThat(data.getRiskLevel()).isEqualTo("NOT_RATED");
        assertThat(data.getRiskLevelNote()).contains("不输出风险等级");
        assertThat(data.getDimensions()).hasSize(9);
        assertThat(data.getMissingAgents()).hasSize(10);
        assertThat(data.getDataCompleteness()).isLessThan(new BigDecimal("0.50"));
        assertThat(data.getPrimaryRepaymentAnalysis()).contains("无法评估");
        assertThat(data.getAmountAndTermConcerns()).anyMatch(concern -> concern.contains("不输出最终审批金额"));
        assertThat(data.getMitigationSuggestions()).isNotEmpty();
        assertThat(result.getDataGaps()).isNotEmpty();
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("不代替最终审批人决策"));
    }

    @Test
    void 尽调报告Agent输出18章草稿并标记导出不可用() {
        long taskId = submittedTask();

        AgentResult<DueDiligenceReportData> result = agentRuntime.execute(
                DdAgentCatalog.DUE_DILIGENCE_REPORT, taskId, null);

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        DueDiligenceReportData data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.getChapters()).hasSize(18);
        assertThat(data.getReportStatus()).isEqualTo("AI_DRAFT_INCOMPLETE");
        assertThat(data.getReviewStatus()).isEqualTo("PENDING_MANUAL_REVIEW");
        assertThat(data.getReportVersion()).isNotNull();
        assertThat(data.getCompleteness()).isLessThan(new BigDecimal("0.50"));
        assertThat(data.getMissingInputs()).isNotEmpty();
        assertThat(data.getValidationIssues()).isNotEmpty();
        assertThat(data.getExportInfo().getWordStatus()).isEqualTo("NOT_AVAILABLE");
        assertThat(data.getExportInfo().getPdfStatus()).isEqualTo("NOT_AVAILABLE");
        assertThat(data.getExportInfo().getNote()).contains("未接入");
        assertThat(data.getGenerationNote()).contains("复核");
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("复核"));

        DdDueDiligenceReport persisted = reportService.latest(taskId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getReportVersion()).isEqualTo(data.getReportVersion());
        assertThat(persisted.getContentJson()).contains("CREDIT_APPLICATION");
        assertThat(persisted.getExportStatus()).isEqualTo("NOT_AVAILABLE");
    }

    @Test
    void 重复生成报告按任务递增版本且历史版本不被覆盖() {
        long taskId = submittedTask();

        AgentResult<DueDiligenceReportData> first = agentRuntime.execute(
                DdAgentCatalog.DUE_DILIGENCE_REPORT, taskId, null);
        AgentResult<DueDiligenceReportData> second = agentRuntime.execute(
                DdAgentCatalog.DUE_DILIGENCE_REPORT, taskId, null);

        int firstVersion = first.getData().getReportVersion();
        int secondVersion = second.getData().getReportVersion();
        assertThat(secondVersion).isEqualTo(firstVersion + 1);
        assertThat(reportService.latest(taskId).getReportVersion()).isEqualTo(secondVersion);
    }

    private long submittedTask() {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西综合风险评估测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APPAGT000007");
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
