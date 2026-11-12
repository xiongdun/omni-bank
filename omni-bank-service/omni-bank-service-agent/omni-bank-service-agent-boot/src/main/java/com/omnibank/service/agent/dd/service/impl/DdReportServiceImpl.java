package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.dto.report.ReportQueryReq;
import com.omnibank.service.agent.dd.dto.report.ReportReviewReq;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.entity.DdReportReview;
import com.omnibank.service.agent.dd.enums.ReportReviewAction;
import com.omnibank.service.agent.dd.enums.ReportReviewStatus;
import com.omnibank.service.agent.dd.enums.ReportStatus;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.event.DdReanalysisRequestedEvent;
import com.omnibank.service.agent.dd.mapper.DdDueDiligenceReportMapper;
import com.omnibank.service.agent.dd.mapper.DdReportReviewMapper;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 尽调报告实现：版本号在任务内递增，章节内容与证据引用以 JSON 保存；复核动作校验状态流转并留痕
 */
@Service
@RequiredArgsConstructor
public class DdReportServiceImpl
        extends ServiceImpl<DdDueDiligenceReportMapper, DdDueDiligenceReport>
        implements DdReportService {

    private final DdJsonSupport jsonSupport;

    private final DdReportReviewMapper reviewMapper;

    private final DdTaskService taskService;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public DdDueDiligenceReport createVersion(Long taskId, String title, String reportStatus,
                                              String reviewStatus, Object chapters, List<String> evidenceIds,
                                              int evidenceCount, int dataGapCount, int supplementCount,
                                              String exportStatus, String modelVersion, String promptVersion) {
        int reportVersion = nextVersion(taskId);
        DdDueDiligenceReport report = new DdDueDiligenceReport();
        report.setTaskId(taskId);
        report.setReportNo("RPT" + taskId + "-V" + reportVersion);
        report.setReportVersion(reportVersion);
        report.setReportStatus(reportStatus);
        report.setReviewStatus(reviewStatus);
        report.setTitle(title);
        report.setContentJson(jsonSupport.write(chapters));
        report.setEvidenceIds(jsonSupport.write(evidenceIds == null ? List.of() : evidenceIds));
        report.setEvidenceCount(evidenceCount);
        report.setDataGapCount(dataGapCount);
        report.setSupplementCount(supplementCount);
        report.setExportStatus(exportStatus);
        report.setModelVersion(modelVersion);
        report.setPromptVersion(promptVersion);
        report.setGeneratedAt(LocalDateTime.now());
        report.setVersion(0);
        save(report);
        return report;
    }

    @Override
    public DdDueDiligenceReport latest(Long taskId) {
        return lambdaQuery()
                .eq(DdDueDiligenceReport::getTaskId, taskId)
                .orderByDesc(DdDueDiligenceReport::getReportVersion)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public IPage<DdDueDiligenceReport> page(Long taskId, ReportQueryReq req) {
        taskService.requireTask(taskId);
        return lambdaQuery()
                .eq(DdDueDiligenceReport::getTaskId, taskId)
                .eq(StringUtils.hasText(req.getReportStatus()),
                        DdDueDiligenceReport::getReportStatus, req.getReportStatus())
                .orderByDesc(DdDueDiligenceReport::getReportVersion)
                .page(new Page<>(req.getCurrent(), req.getSize()));
    }

    @Override
    public DdDueDiligenceReport requireReport(Long taskId, Integer reportVersion) {
        taskService.requireTask(taskId);
        DdDueDiligenceReport report = lambdaQuery()
                .eq(DdDueDiligenceReport::getTaskId, taskId)
                .eq(DdDueDiligenceReport::getReportVersion, reportVersion)
                .one();
        if (report == null) {
            throw new BizException(DdErrorCode.REPORT_NOT_FOUND);
        }
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DdDueDiligenceReport review(Long taskId, Integer reportVersion, ReportReviewReq req,
                                       String reviewer) {
        DdDueDiligenceReport report = requireReport(taskId, reportVersion);
        ReportStatus from = parseStatus(report.getReportStatus());
        ReportReviewAction action = req.getActionType();
        ReportStatus to = nextStatus(from, action);
        requireReason(action, req);

        String amendedContentJson = null;
        String originalValue = null;
        if (action == ReportReviewAction.AMEND) {
            if (!StringUtils.hasText(req.getFieldKey()) || !StringUtils.hasText(req.getAmendedValue())) {
                throw new BizException(DdErrorCode.REPORT_REVIEW_PARAM_INVALID, "修正动作须指定章节编码与修正后内容");
            }
            List<Map<String, Object>> chapters = jsonSupport.read(report.getContentJson(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            Map<String, Object> target = chapters.stream()
                    .filter(chapter -> req.getFieldKey().equals(chapter.get("chapterCode")))
                    .findFirst()
                    .orElseThrow(() -> new BizException(DdErrorCode.REPORT_CHAPTER_NOT_FOUND));
            Object original = target.get("content");
            originalValue = original == null ? "" : String.valueOf(original);
            target.put("content", req.getAmendedValue());
            amendedContentJson = jsonSupport.write(chapters);
        }

        DdDueDiligenceReport update = new DdDueDiligenceReport();
        update.setReportStatus(to.name());
        update.setReviewStatus(reviewStatusOf(to).name());
        update.setVersion(report.getVersion() + 1);
        if (amendedContentJson != null) {
            update.setContentJson(amendedContentJson);
        }
        boolean updated = update(update, Wrappers.<DdDueDiligenceReport>lambdaUpdate()
                .eq(DdDueDiligenceReport::getId, report.getId())
                .eq(DdDueDiligenceReport::getVersion, report.getVersion()));
        if (!updated) {
            throw new BizException(DdErrorCode.REPORT_CONFLICT);
        }

        DdReportReview reviewRecord = new DdReportReview();
        reviewRecord.setTaskId(taskId);
        reviewRecord.setReportId(report.getId());
        reviewRecord.setReportVersion(reportVersion);
        reviewRecord.setActionType(action.name());
        reviewRecord.setFromStatus(from.name());
        reviewRecord.setToStatus(to.name());
        reviewRecord.setFieldKey(action == ReportReviewAction.AMEND ? req.getFieldKey() : null);
        reviewRecord.setOriginalValue(originalValue);
        reviewRecord.setAmendedValue(action == ReportReviewAction.AMEND ? req.getAmendedValue() : null);
        reviewRecord.setReviewer(reviewer);
        reviewRecord.setReason(req.getReason());
        reviewRecord.setRemark(req.getRemark());
        reviewRecord.setEvidenceIds(jsonSupport.write(
                req.getEvidenceIds() == null ? List.of() : req.getEvidenceIds()));
        reviewMapper.insert(reviewRecord);

        report.setReportStatus(to.name());
        report.setReviewStatus(reviewStatusOf(to).name());
        report.setVersion(report.getVersion() + 1);
        if (amendedContentJson != null) {
            report.setContentJson(amendedContentJson);
        }
        linkTaskStatus(taskId, action);
        return report;
    }

    /** 报告复核联动任务状态：定稿完成、驳回/补材料退回、要求重新分析；任务不在对应状态时不强耦合 */
    private void linkTaskStatus(Long taskId, ReportReviewAction action) {
        switch (action) {
            case FINALIZE -> taskService.markCompletedIfWaitingForReview(taskId);
            case REJECT, REQUEST_MATERIAL -> taskService.markReturnedIfWaitingForReview(taskId);
            case REANALYZE -> {
                if (TaskStatus.RETURNED.name().equals(taskService.requireTask(taskId).getTaskStatus())) {
                    eventPublisher.publishEvent(
                            new DdReanalysisRequestedEvent(taskId, Set.of(), "报告要求重新分析"));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public List<DdReportReview> reviews(Long taskId, Integer reportVersion) {
        requireReport(taskId, reportVersion);
        return reviewMapper.selectList(Wrappers.<DdReportReview>lambdaQuery()
                .eq(DdReportReview::getTaskId, taskId)
                .eq(DdReportReview::getReportVersion, reportVersion)
                .orderByAsc(DdReportReview::getCreateTime)
                .orderByAsc(DdReportReview::getId));
    }

    @Override
    public List<DdDueDiligenceReport> listByEvidence(Long taskId, Long evidenceId) {
        return lambdaQuery()
                .eq(DdDueDiligenceReport::getTaskId, taskId)
                .like(DdDueDiligenceReport::getEvidenceIds, "\"" + evidenceId + "\"")
                .orderByDesc(DdDueDiligenceReport::getReportVersion)
                .list();
    }

    @Override
    public List<DdReportReview> listReviewsByEvidence(Long taskId, Long evidenceId) {
        return reviewMapper.selectList(Wrappers.<DdReportReview>lambdaQuery()
                .eq(DdReportReview::getTaskId, taskId)
                .like(DdReportReview::getEvidenceIds, "\"" + evidenceId + "\"")
                .orderByAsc(DdReportReview::getCreateTime)
                .orderByAsc(DdReportReview::getId));
    }

    private ReportStatus nextStatus(ReportStatus from, ReportReviewAction action) {
        if (action == null) {
            throw new BizException(DdErrorCode.REPORT_REVIEW_PARAM_INVALID, "复核动作不能为空");
        }
        if (!allowed(from, action)) {
            throw new BizException(DdErrorCode.REPORT_STATUS_NOT_ALLOWED,
                    "报告当前状态「" + from.getDesc() + "」不允许执行「" + action.getDesc() + "」");
        }
        return switch (action) {
            case SUBMIT -> ReportStatus.WAITING_FOR_REVIEW;
            case CONFIRM -> ReportStatus.REVIEWED;
            case SUPPLEMENT -> from;
            case AMEND -> ReportStatus.WAITING_FOR_REVIEW;
            case REJECT, REQUEST_MATERIAL -> ReportStatus.RETURNED_FOR_SUPPLEMENT;
            case REANALYZE -> ReportStatus.REANALYZING;
            case FINALIZE -> ReportStatus.FINALIZED;
        };
    }

    private boolean allowed(ReportStatus from, ReportReviewAction action) {
        if (from.isTerminal()) {
            return false;
        }
        return switch (action) {
            case SUBMIT -> from == ReportStatus.AI_GENERATED;
            case CONFIRM -> from == ReportStatus.WAITING_FOR_REVIEW;
            case SUPPLEMENT, AMEND, REJECT, REQUEST_MATERIAL ->
                    from == ReportStatus.WAITING_FOR_REVIEW || from == ReportStatus.REVIEWED;
            case REANALYZE -> from == ReportStatus.RETURNED_FOR_SUPPLEMENT;
            case FINALIZE -> from == ReportStatus.REVIEWED;
        };
    }

    private void requireReason(ReportReviewAction action, ReportReviewReq req) {
        boolean reasonRequired = switch (action) {
            case AMEND, REJECT, REQUEST_MATERIAL, REANALYZE -> true;
            default -> false;
        };
        if (reasonRequired && !StringUtils.hasText(req.getReason())) {
            throw new BizException(DdErrorCode.REPORT_REVIEW_PARAM_INVALID,
                    "「" + action.getDesc() + "」须填写复核原因");
        }
        if (action == ReportReviewAction.SUPPLEMENT && !StringUtils.hasText(req.getRemark())) {
            throw new BizException(DdErrorCode.REPORT_REVIEW_PARAM_INVALID, "补充说明须填写复核意见");
        }
    }

    private ReportStatus parseStatus(String status) {
        try {
            return ReportStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(DdErrorCode.REPORT_STATUS_NOT_ALLOWED, "报告状态非法：" + status);
        }
    }

    private ReportReviewStatus reviewStatusOf(ReportStatus status) {
        return switch (status) {
            case DRAFT, AI_GENERATED -> ReportReviewStatus.NOT_SUBMITTED;
            case WAITING_FOR_REVIEW -> ReportReviewStatus.PENDING_MANUAL_REVIEW;
            case RETURNED_FOR_SUPPLEMENT -> ReportReviewStatus.RETURNED_FOR_SUPPLEMENT;
            case REANALYZING -> ReportReviewStatus.REANALYZING;
            case REVIEWED -> ReportReviewStatus.REVIEWED;
            case FINALIZED -> ReportReviewStatus.FINALIZED;
        };
    }

    private int nextVersion(Long taskId) {
        DdDueDiligenceReport latest = latest(taskId);
        return latest == null ? 1 : latest.getReportVersion() + 1;
    }
}
