package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.report.ReportQueryReq;
import com.omnibank.service.agent.dd.dto.report.ReportReviewReq;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.entity.DdReportReview;
import java.util.List;

/**
 * 尽调报告服务：版本生成、查询与人工复核状态流转
 */
public interface DdReportService {

    /**
     * 新增一个报告版本：版本号取当前任务最大版本 + 1，章节内容序列化后落库
     */
    DdDueDiligenceReport createVersion(Long taskId, String title, String reportStatus, String reviewStatus,
                                       Object chapters, List<String> evidenceIds, int evidenceCount,
                                       int dataGapCount, int supplementCount, String exportStatus,
                                       String modelVersion, String promptVersion);

    /** 任务最新报告版本，不存在返回 null */
    DdDueDiligenceReport latest(Long taskId);

    /** 任务报告版本分页 */
    IPage<DdDueDiligenceReport> page(Long taskId, ReportQueryReq req);

    /** 查询指定版本报告，不存在抛业务异常 */
    DdDueDiligenceReport requireReport(Long taskId, Integer reportVersion);

    /** 执行复核动作：校验流转、修正章节内容并写入留痕记录 */
    DdDueDiligenceReport review(Long taskId, Integer reportVersion, ReportReviewReq req, String reviewer);

    /** 查询指定版本报告的复核留痕，按时间正序 */
    List<DdReportReview> reviews(Long taskId, Integer reportVersion);
}
