package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报告人工复核动作：每个动作对应固定的状态流转，修正动作须保留原始值与修正值
 */
@Getter
@AllArgsConstructor
public enum ReportReviewAction {

    SUBMIT("提交复核"),
    CONFIRM("复核确认"),
    SUPPLEMENT("补充说明"),
    AMEND("修正章节内容"),
    REJECT("复核驳回"),
    REQUEST_MATERIAL("要求补充材料"),
    REANALYZE("要求重新分析"),
    FINALIZE("定稿");

    private final String desc;
}
