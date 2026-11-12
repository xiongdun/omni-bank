package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.dto.report.ReportReviewReq;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.entity.DdReportReview;
import com.omnibank.service.agent.dd.enums.ReportReviewAction;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P7 报告人工复核集成测试：状态机流转、修正留痕、驳回与重新分析、查询接口
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@AutoConfigureMockMvc
class DdReportReviewIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DdReportService reportService;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 报告提交复核后确认定稿并按顺序留存复核记录() {
        DdDueDiligenceReport report = generatedReport();
        assertThat(report.getReportStatus()).isEqualTo("AI_GENERATED");
        assertThat(report.getReviewStatus()).isEqualTo("NOT_SUBMITTED");

        DdDueDiligenceReport waiting = review(report, ReportReviewAction.SUBMIT, null, null, null,
                "提交复核", "reviewer01");
        assertThat(waiting.getReportStatus()).isEqualTo("WAITING_FOR_REVIEW");
        assertThat(waiting.getReviewStatus()).isEqualTo("PENDING_MANUAL_REVIEW");

        DdDueDiligenceReport reviewed = review(report, ReportReviewAction.CONFIRM, null, null, null,
                "章节内容与证据一致", "reviewer01");
        assertThat(reviewed.getReportStatus()).isEqualTo("REVIEWED");
        assertThat(reviewed.getReviewStatus()).isEqualTo("REVIEWED");

        DdDueDiligenceReport finalized = review(report, ReportReviewAction.FINALIZE, null, null, null,
                "定稿归档", "reviewer01");
        assertThat(finalized.getReportStatus()).isEqualTo("FINALIZED");

        List<DdReportReview> records = reportService.reviews(report.getTaskId(), report.getReportVersion());
        assertThat(records).extracting(DdReportReview::getActionType)
                .containsExactly("SUBMIT", "CONFIRM", "FINALIZE");
        assertThat(records).allSatisfy(record -> assertThat(record.getReviewer()).isEqualTo("reviewer01"));

        assertThatThrownBy(() -> review(report, ReportReviewAction.CONFIRM, null, null, null,
                "重复确认", "reviewer01"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230130"));
    }

    @Test
    void 修正章节保留原始值与修正值并回到待复核() {
        DdDueDiligenceReport report = generatedReport();
        review(report, ReportReviewAction.SUBMIT, null, null, null, "提交复核", "reviewer01");
        review(report, ReportReviewAction.CONFIRM, null, null, null, "复核通过", "reviewer01");

        DdDueDiligenceReport amended = review(report, ReportReviewAction.AMEND, "CREDIT_APPLICATION",
                "人工修正后的申请信息", "更正申请金额", "已与客户核实", "reviewer02");

        assertThat(amended.getReportStatus()).isEqualTo("WAITING_FOR_REVIEW");
        assertThat(amended.getContentJson()).contains("人工修正后的申请信息");
        assertThat(reportService.requireReport(report.getTaskId(), report.getReportVersion()).getContentJson())
                .contains("人工修正后的申请信息");

        List<DdReportReview> records = reportService.reviews(report.getTaskId(), report.getReportVersion());
        DdReportReview last = records.get(records.size() - 1);
        assertThat(last.getActionType()).isEqualTo("AMEND");
        assertThat(last.getFieldKey()).isEqualTo("CREDIT_APPLICATION");
        assertThat(last.getOriginalValue()).isNotBlank().doesNotContain("人工修正后的申请信息");
        assertThat(last.getAmendedValue()).isEqualTo("人工修正后的申请信息");
        assertThat(last.getReviewer()).isEqualTo("reviewer02");
        assertThat(last.getReason()).isEqualTo("更正申请金额");
        assertThat(last.getRemark()).isEqualTo("已与客户核实");
    }

    @Test
    void 驳回后要求重新分析并按状态机拒绝非法流转() {
        DdDueDiligenceReport report = generatedReport();
        review(report, ReportReviewAction.SUBMIT, null, null, null, "提交复核", "reviewer01");

        assertThatThrownBy(() -> review(report, ReportReviewAction.REJECT, null, null, null, null,
                "reviewer01"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230133"));

        DdDueDiligenceReport returned = review(report, ReportReviewAction.REJECT, null, null,
                "财务数据缺失", "退回补充", "reviewer01");
        assertThat(returned.getReportStatus()).isEqualTo("RETURNED_FOR_SUPPLEMENT");
        assertThat(returned.getReviewStatus()).isEqualTo("RETURNED_FOR_SUPPLEMENT");

        assertThatThrownBy(() -> review(report, ReportReviewAction.CONFIRM, null, null, null,
                "退回状态下不可确认", "reviewer01"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230130"));

        DdDueDiligenceReport reanalyzing = review(report, ReportReviewAction.REANALYZE, null, null,
                "补资料后重新分析", "重新分析", "reviewer01");
        assertThat(reanalyzing.getReportStatus()).isEqualTo("REANALYZING");

        assertThatThrownBy(() -> review(report, ReportReviewAction.FINALIZE, null, null, null,
                "分析中不可定稿", "reviewer01"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230130"));
    }

    @Test
    void 报告查询与复核接口返回版本详情与留痕() throws Exception {
        DdDueDiligenceReport report = generatedReport();
        long taskId = report.getTaskId();
        int version = report.getReportVersion();

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/reports", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000200"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].reportStatus").value("AI_GENERATED"));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/reports/{reportVersion}", taskId, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000200"))
                .andExpect(jsonPath("$.data.reportVersion").value(version))
                .andExpect(jsonPath("$.data.chapters.length()").value(18))
                .andExpect(jsonPath("$.data.exportStatus").value("NOT_AVAILABLE"));

        mockMvc.perform(post("/v1/dd/tasks/{taskId}/reports/{reportVersion}/review", taskId, version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SUBMIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000200"))
                .andExpect(jsonPath("$.data.reportStatus").value("WAITING_FOR_REVIEW"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING_MANUAL_REVIEW"));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/reports/{reportVersion}/reviews", taskId, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000200"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].actionType").value("SUBMIT"))
                .andExpect(jsonPath("$.data[0].fromStatus").value("AI_GENERATED"))
                .andExpect(jsonPath("$.data[0].toStatus").value("WAITING_FOR_REVIEW"))
                .andExpect(jsonPath("$.data[0].reviewer").value("system"));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/reports/{reportVersion}", taskId, 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("230129"));
    }

    private DdDueDiligenceReport generatedReport() {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西报告复核测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APPREVIEW" + System.nanoTime());
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

        DdDueDiligenceReport report = reportService.latest(created.getTaskId());
        assertThat(report).isNotNull();
        return report;
    }

    private DdDueDiligenceReport review(DdDueDiligenceReport report, ReportReviewAction action,
                                        String fieldKey, String amendedValue, String reason, String remark,
                                        String reviewer) {
        ReportReviewReq req = new ReportReviewReq();
        req.setActionType(action);
        req.setFieldKey(fieldKey);
        req.setAmendedValue(amendedValue);
        req.setReason(reason);
        req.setRemark(remark);
        return reportService.review(report.getTaskId(), report.getReportVersion(), req, reviewer);
    }
}
