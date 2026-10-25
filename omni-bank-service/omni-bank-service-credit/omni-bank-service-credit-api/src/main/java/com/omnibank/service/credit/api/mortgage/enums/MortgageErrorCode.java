package com.omnibank.service.credit.api.mortgage.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 抵押场景（mortgage）错误码：号段 200001-209999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum MortgageErrorCode implements ErrorCode {

    MORTGAGE_NOT_FOUND("200001", "抵押物信息不存在"),
    MORTGAGE_NOT_VALUED("200002", "抵押物未完成评估");

    private final String code;

    private final String msg;
}
