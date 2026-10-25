package com.omnibank.service.payment.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付域错误码：号段 150001-159999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    CHANNEL_UNAVAILABLE("150001", "支付渠道不可用"),
    PAY_ORDER_DUPLICATED("150002", "支付单重复提交");

    private final String code;

    private final String msg;
}
