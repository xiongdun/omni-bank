package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 尽调报告输出：按固定模板生成 18 章结构化草稿，引用上游证据与缺口；
 * 报告须客户经理复核，P7 仅输出导出元信息，不生成 Word/PDF 文件。
 */
@Data
public class DueDiligenceReportData {

    /** 报告ID，落库后回填 */
    private Long reportId;

    /** 任务ID */
    private Long taskId;

    /** 报告编号 */
    private String reportNo;

    /** 报告版本，同一任务递增 */
    private Integer reportVersion;

    /** 报告状态：AI_DRAFT_COMPLETE / AI_DRAFT_INCOMPLETE */
    private String reportStatus;

    /** 复核状态固定 PENDING_MANUAL_REVIEW */
    private String reviewStatus;

    /** 报告标题 */
    private String title;

    /** 模板版本 */
    private String templateVersion;

    /** 报告章节 */
    private List<ReportChapter> chapters;

    /** 引用证据数量 */
    private int evidenceCount;

    /** 数据缺口数量 */
    private int dataGapCount;

    /** 补充材料请求数量 */
    private int supplementRequestCount;

    /** 必备章节完成度 */
    private BigDecimal completeness;

    /** 报告可信程度 */
    private BigDecimal confidence;

    /** 缺失输入项 */
    private List<String> missingInputs;

    /** 校验问题 */
    private List<String> validationIssues;

    /** 导出信息 */
    private ExportInfo exportInfo;

    /** 生成说明与使用边界 */
    private String generationNote;

    /** 报告章节 */
    @Data
    public static class ReportChapter {

        /** 章节序号 */
        private int chapterNo;

        /** 章节编码 */
        private String chapterCode;

        /** 章节标题 */
        private String title;

        /** 章节正文 */
        private String content;

        /** 状态：AVAILABLE / MISSING_INPUT / AGGREGATED / MANUAL_REVIEW_PENDING */
        private String status;

        /** 引用证据 ID */
        private List<String> evidenceIds;

        /** 章节数据缺口 */
        private List<String> dataGaps;

        /** 章节局限说明 */
        private String limitation;
    }

    /** 导出信息：P7 不生成真实文件 */
    @Data
    public static class ExportInfo {

        /** Word 导出状态 */
        private String wordStatus;

        /** PDF 导出状态 */
        private String pdfStatus;

        /** 导出说明 */
        private String note;
    }
}
