package com.omnibank.service.credit.api.limit.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 额度场景（limit）错误码：号段 190001-199999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum LimitErrorCode implements ErrorCode {

    LIMIT_NOT_FOUND("190001", "额度记录不存在"),
    LIMIT_INSUFFICIENT("190002", "可用额度不足");

    private final String code;

    private final String msg;
}
