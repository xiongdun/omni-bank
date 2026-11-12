package com.omnibank.service.agent.dd.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.common.core.context.UserContext;
import com.omnibank.common.core.page.PageResult;
import com.omnibank.common.core.result.Result;
import com.omnibank.common.mybatis.util.PageResults;
import com.omnibank.service.agent.dd.dto.report.ReportDetailVO;
import com.omnibank.service.agent.dd.dto.report.ReportQueryReq;
import com.omnibank.service.agent.dd.dto.report.ReportReviewReq;
import com.omnibank.service.agent.dd.dto.report.ReportReviewVO;
import com.omnibank.service.agent.dd.dto.report.ReportVersionVO;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.entity.DdReportReview;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 尽调报告接口：报告版本查询与人工复核，路径经网关 StripPrefix 后暴露为 /api/v1/dd/tasks/{taskId}/reports
 */
@RestController
@RequestMapping("/v1/dd/tasks/{taskId}/reports")
@RequiredArgsConstructor
public class DdReportController {

    private final DdReportService reportService;

    private final DdJsonSupport jsonSupport;

    /** 报告版本分页 */
    @GetMapping
    public Result<PageResult<ReportVersionVO>> page(@PathVariable Long taskId, ReportQueryReq req) {
        IPage<ReportVersionVO> page = reportService.page(taskId, req).convert(this::versionOf);
        return Result.success(PageResults.from(page));
    }

    /** 报告详情 */
    @GetMapping("/{reportVersion}")
    public Result<ReportDetailVO> detail(@PathVariable Long taskId, @PathVariable Integer reportVersion) {
        return Result.success(detailOf(reportService.requireReport(taskId, reportVersion)));
    }

    /** 报告人工复核：提交、确认、补充说明、修正、驳回、要求补材料、要求重新分析、定稿 */
    @PostMapping("/{reportVersion}/review")
    public Result<ReportDetailVO> review(@PathVariable Long taskId, @PathVariable Integer reportVersion,
                                         @Valid @RequestBody ReportReviewReq req) {
        DdDueDiligenceReport report = reportService.review(taskId, reportVersion, req, operator());
        return Result.success(detailOf(report));
    }

    /** 报告复核留痕 */
    @GetMapping("/{reportVersion}/reviews")
    public Result<List<ReportReviewVO>> reviews(@PathVariable Long taskId, @PathVariable Integer reportVersion) {
        return Result.success(reportService.reviews(taskId, reportVersion).stream()
                .map(this::reviewOf)
                .toList());
    }

    private ReportVersionVO versionOf(DdDueDiligenceReport report) {
        ReportVersionVO vo = new ReportVersionVO();
        fill(vo, report);
        return vo;
    }

    private ReportDetailVO detailOf(DdDueDiligenceReport report) {
        ReportDetailVO vo = new ReportDetailVO();
        fill(vo, report);
        vo.setChapters(jsonSupport.read(report.getContentJson(),
                new TypeReference<List<Map<String, Object>>>() {
                }));
        vo.setEvidenceIds(jsonSupport.readStringList(report.getEvidenceIds()));
        return vo;
    }

    private void fill(ReportVersionVO vo, DdDueDiligenceReport report) {
        vo.setId(report.getId());
        vo.setTaskId(report.getTaskId());
        vo.setReportNo(report.getReportNo());
        vo.setReportVersion(report.getReportVersion());
        vo.setReportStatus(report.getReportStatus());
        vo.setReviewStatus(report.getReviewStatus());
        vo.setTitle(report.getTitle());
        vo.setEvidenceCount(report.getEvidenceCount());
        vo.setDataGapCount(report.getDataGapCount());
        vo.setSupplementCount(report.getSupplementCount());
        vo.setExportStatus(report.getExportStatus());
        vo.setModelVersion(report.getModelVersion());
        vo.setPromptVersion(report.getPromptVersion());
        vo.setGeneratedAt(report.getGeneratedAt());
    }

    private ReportReviewVO reviewOf(DdReportReview review) {
        ReportReviewVO vo = new ReportReviewVO();
        vo.setId(review.getId());
        vo.setActionType(review.getActionType());
        vo.setFromStatus(review.getFromStatus());
        vo.setToStatus(review.getToStatus());
        vo.setFieldKey(review.getFieldKey());
        vo.setOriginalValue(review.getOriginalValue());
        vo.setAmendedValue(review.getAmendedValue());
        vo.setReviewer(review.getReviewer());
        vo.setReason(review.getReason());
        vo.setRemark(review.getRemark());
        vo.setEvidenceIds(jsonSupport.readStringList(review.getEvidenceIds()));
        vo.setCreateTime(review.getCreateTime());
        return vo;
    }

    private String operator() {
        return StringUtils.hasText(UserContext.getUsername()) ? UserContext.getUsername() : "system";
    }
}
