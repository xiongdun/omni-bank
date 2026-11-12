package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.mapper.DdDueDiligenceReportMapper;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 尽调报告版本实现：版本号在任务内递增，章节内容与证据引用以 JSON 保存
 */
@Service
@RequiredArgsConstructor
public class DdReportServiceImpl
        extends ServiceImpl<DdDueDiligenceReportMapper, DdDueDiligenceReport>
        implements DdReportService {

    private final DdJsonSupport jsonSupport;

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

    private int nextVersion(Long taskId) {
        DdDueDiligenceReport latest = latest(taskId);
        return latest == null ? 1 : latest.getReportVersion() + 1;
    }
}
