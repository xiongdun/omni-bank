package com.omnibank.service.agent.dd.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdDueDiligenceReport;
import com.omnibank.service.agent.dd.service.DdReportExportService;
import com.omnibank.service.agent.dd.service.DdReportService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 尽调报告 Word 导出：按报告版本将章节正文渲染为 docx，不改变报告状态与版本
 */
@Service
@RequiredArgsConstructor
public class DdReportExportServiceImpl implements DdReportExportService {

    private static final Logger log = LoggerFactory.getLogger(DdReportExportServiceImpl.class);

    private static final String DISCLAIMER =
            "本报告由 AI 基于上游分析结果自动生成草稿，须客户经理复核确认，不代表授信审批通过；"
                    + "AI 不输出最终审批金额、不输出合规结论";

    private final DdReportService reportService;

    private final DdJsonSupport jsonSupport;

    @Override
    public ExportFile exportWord(Long taskId, Integer reportVersion) {
        DdDueDiligenceReport report = reportService.requireReport(taskId, reportVersion);
        List<Map<String, Object>> chapters = jsonSupport.read(report.getContentJson(),
                new TypeReference<List<Map<String, Object>>>() {
                });
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTitle(document, report);
            writeMeta(document, report);
            chapters.forEach(chapter -> writeChapter(document, chapter));
            writeDisclaimer(document);
            document.write(out);
            log.info("尽调报告导出完成 taskId={} reportVersion={} bytes={}", taskId, reportVersion,
                    out.size());
            return new ExportFile(fileName(report), out.toByteArray());
        } catch (IOException e) {
            log.error("尽调报告导出失败 taskId={} reportVersion={}", taskId, reportVersion, e);
            throw new BizException(DdErrorCode.REPORT_EXPORT_FAILED);
        }
    }

    private void writeTitle(XWPFDocument document, DdDueDiligenceReport report) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(Objects.toString(report.getTitle(), "尽调报告"));
    }

    private void writeMeta(XWPFDocument document, DdDueDiligenceReport report) {
        metaLine(document, "报告编号：" + Objects.toString(report.getReportNo(), "-")
                + "    版本：v" + report.getReportVersion());
        metaLine(document, "报告状态：" + Objects.toString(report.getReportStatus(), "-")
                + "    复核状态：" + Objects.toString(report.getReviewStatus(), "-"));
        metaLine(document, "生成时间：" + Objects.toString(report.getGeneratedAt(), "-"));
        metaLine(document, "证据 " + report.getEvidenceCount() + " 条    数据缺口 "
                + report.getDataGapCount() + " 项    补充材料 " + report.getSupplementCount() + " 项");
    }

    private void metaLine(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(10);
        run.setColor("606266");
        run.setText(text);
    }

    private void writeChapter(XWPFDocument document, Map<String, Object> chapter) {
        Object chapterNo = chapter.get("chapterNo");
        String title = Objects.toString(chapter.get("title"), Objects.toString(chapter.get("chapterCode"), ""));
        String status = Objects.toString(chapter.get("status"), "");
        XWPFParagraph heading = document.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(13);
        headingRun.setText((chapterNo == null ? "" : chapterNo + "、") + title
                + (status.isBlank() ? "" : "（" + status + "）"));

        String content = Objects.toString(chapter.get("content"), "");
        for (String line : content.split("\\R")) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontSize(11);
            run.setText(line);
        }

        Object dataGaps = chapter.get("dataGaps");
        if (dataGaps instanceof List<?> gaps && !gaps.isEmpty()) {
            chapterNote(document, "数据缺口：" + String.join("；", gaps.stream().map(String::valueOf).toList()));
        }
        Object limitation = chapter.get("limitation");
        if (limitation != null && !String.valueOf(limitation).isBlank()) {
            chapterNote(document, "局限说明：" + limitation);
        }
        Object evidenceIds = chapter.get("evidenceIds");
        if (evidenceIds instanceof List<?> ids && !ids.isEmpty()) {
            chapterNote(document, "引用证据：" + String.join("、", ids.stream().map(String::valueOf).toList()));
        }
    }

    private void chapterNote(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(9);
        run.setColor("909399");
        run.setText(text);
    }

    private void writeDisclaimer(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(9);
        run.setColor("909399");
        run.setText(DISCLAIMER);
    }

    private String fileName(DdDueDiligenceReport report) {
        return "尽调报告-" + Objects.toString(report.getReportNo(), String.valueOf(report.getTaskId()))
                + "-V" + report.getReportVersion() + ".docx";
    }
}
