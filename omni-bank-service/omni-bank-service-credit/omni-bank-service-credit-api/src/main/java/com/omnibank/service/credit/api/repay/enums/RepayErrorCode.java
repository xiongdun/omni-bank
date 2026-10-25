package com.omnibank.service.credit.api.repay.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 还款场景（repay）错误码：号段 180001-189999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum RepayErrorCode implements ErrorCode {

    REPAY_PLAN_NOT_FOUND("180001", "还款计划不存在"),
    REPAY_STATE_ERROR("180002", "还款状态流转非法");

    private final String code;

    private final String msg;
}
