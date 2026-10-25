package com.omnibank.service.credit.api.credit.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 授信场景（credit）错误码：号段 120001-129999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum CreditErrorCode implements ErrorCode {

    APPLY_NOT_FOUND("120001", "进件申请不存在"),
    APPROVAL_STATE_ERROR("120002", "审批状态流转非法");

    private final String code;

    private final String msg;
}
