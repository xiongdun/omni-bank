package com.omnibank.service.loan.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 借据域错误码：号段 13000-13999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum LoanErrorCode implements ErrorCode {

    LOAN_NOT_FOUND("13001", "借据不存在"),
    LOAN_STATE_ERROR("13002", "借据状态流转非法");

    private final String code;

    private final String msg;
}
