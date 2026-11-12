package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 尽调报告状态：状态流转统一由 DdReportService 校验，Controller 不得直接改状态
 */
@Getter
@AllArgsConstructor
public enum ReportStatus {

    DRAFT("草稿"),
    AI_GENERATED("AI 生成完成"),
    WAITING_FOR_REVIEW("等待人工复核"),
    RETURNED_FOR_SUPPLEMENT("退回补充材料"),
    REANALYZING("重新分析中"),
    REVIEWED("复核通过"),
    FINALIZED("已定稿");

    private final String desc;

    /** 终态：定稿后不可再复核流转 */
    public boolean isTerminal() {
        return this == FINALIZED;
    }
}
