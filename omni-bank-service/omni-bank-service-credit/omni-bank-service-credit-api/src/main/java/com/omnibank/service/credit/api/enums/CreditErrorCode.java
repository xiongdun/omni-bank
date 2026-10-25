package com.omnibank.service.credit.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信贷域错误码：号段 12000-12999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum CreditErrorCode implements ErrorCode {

    APPLY_NOT_FOUND("12001", "进件申请不存在"),
    APPROVAL_STATE_ERROR("12002", "审批状态流转非法");

    private final String code;

    private final String msg;
}
