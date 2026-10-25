package com.omnibank.common.excel;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 导入导出工具（基于 EasyExcel），管理端报表导出与批量导入使用。
 */
public final class ExcelUtils {

    private ExcelUtils() {
    }

    /** 导出：写入 HTTP 响应，浏览器直接下载（xlsx 格式） */
    public static <T> void write(HttpServletResponse response, String fileName, String sheetName,
                                 Class<T> head, List<T> data) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), head).sheet(sheetName).doWrite(data);
        } catch (Exception e) {
            throw new IllegalStateException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    /** 导入：从输入流同步读取为对象列表（首个工作表） */
    public static <T> List<T> read(InputStream inputStream, Class<T> head) {
        return EasyExcel.read(inputStream).head(head).sheet().doReadSync();
    }
}
