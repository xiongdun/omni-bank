package com.omnibank.service.agent.dd.service;

/**
 * 尽调报告导出：按报告版本生成可下载文件，导出内容与持久化章节保持一致
 */
public interface DdReportExportService {

    /** 导出 Word（docx） */
    ExportFile exportWord(Long taskId, Integer reportVersion);

    /** 导出文件：文件名与二进制内容 */
    record ExportFile(String fileName, byte[] content) {
    }
}
