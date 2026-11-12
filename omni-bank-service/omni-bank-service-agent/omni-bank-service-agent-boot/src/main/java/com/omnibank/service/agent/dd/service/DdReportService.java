package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import java.util.List;

/**
 * 尽调报告版本服务：按任务生成递增版本并持久化章节内容
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
}
