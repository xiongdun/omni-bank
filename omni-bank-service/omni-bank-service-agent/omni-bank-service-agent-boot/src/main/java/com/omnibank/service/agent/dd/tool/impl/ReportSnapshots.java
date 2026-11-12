package com.omnibank.service.agent.dd.tool.impl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 尽调报告相关工具快照：模板、校验结果与版本信息
 */
public final class ReportSnapshots {

    private ReportSnapshots() {
    }

    /** 报告章节模板 */
    public record ChapterTemplate(int chapterNo, String chapterCode, String title,
                                  boolean required, boolean requireEvidence, List<String> sourceAgents) {
    }

    /** 报告模板 */
    public record TemplateResult(String templateVersion, List<ChapterTemplate> chapters) {
    }

    /** 报告章节校验结果 */
    public record ValidationResult(boolean valid, List<String> issues, List<String> missingChapters,
                                   List<String> chaptersWithoutEvidence) {
    }

    /** 报告版本落库结果 */
    public record VersionResult(Long reportId, String reportNo, Integer reportVersion, String reportStatus,
                                LocalDateTime generatedAt) {
    }
}
