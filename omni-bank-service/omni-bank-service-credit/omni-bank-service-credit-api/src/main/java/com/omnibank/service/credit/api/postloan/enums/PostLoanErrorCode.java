package com.omnibank.service.credit.api.postloan.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 贷后场景（postloan）错误码：号段 210001-219999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum PostLoanErrorCode implements ErrorCode {

    POSTLOAN_TASK_NOT_FOUND("210001", "贷后任务不存在"),
    OVERDUE_STATE_ERROR("210002", "逾期状态流转非法");

    private final String code;

    private final String msg;
}
