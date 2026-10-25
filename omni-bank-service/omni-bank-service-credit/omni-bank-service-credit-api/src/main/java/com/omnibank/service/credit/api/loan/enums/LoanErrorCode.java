package com.omnibank.service.credit.api.loan.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 借据场景（loan）错误码：号段 130001-139999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum LoanErrorCode implements ErrorCode {

    LOAN_NOT_FOUND("130001", "借据不存在"),
    LOAN_STATE_ERROR("130002", "借据状态流转非法");

    private final String code;

    private final String msg;
}
