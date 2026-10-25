package com.omnibank.service.product.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 产品域错误码：号段 110001-119999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("110001", "信贷产品不存在"),
    PRODUCT_DISABLED("110002", "产品已停用");

    private final String code;

    private final String msg;
}
