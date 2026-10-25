package com.omnibank.service.customer.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户域错误码：号段 10000-10999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum CustomerErrorCode implements ErrorCode {

    CUSTOMER_NOT_FOUND("10001", "客户不存在"),
    CUSTOMER_ALREADY_EXISTS("10002", "客户已存在");

    private final String code;

    private final String msg;
}
