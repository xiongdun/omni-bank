package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报告复核状态：与报告主状态同步维护，用于列表快速筛选
 */
@Getter
@AllArgsConstructor
public enum ReportReviewStatus {

    NOT_SUBMITTED("未提交复核"),
    PENDING_MANUAL_REVIEW("等待人工复核"),
    RETURNED_FOR_SUPPLEMENT("已退回补充"),
    REANALYZING("重新分析中"),
    REVIEWED("复核通过"),
    FINALIZED("已定稿");

    private final String desc;
}
