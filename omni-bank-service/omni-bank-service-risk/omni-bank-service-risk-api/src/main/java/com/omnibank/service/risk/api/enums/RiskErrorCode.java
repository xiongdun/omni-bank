package com.omnibank.service.risk.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控域错误码：号段 170001-179999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum RiskErrorCode implements ErrorCode {

    DECISION_TIMEOUT("170001", "决策超时"),
    RULE_HIT_REJECT("170002", "命中拒绝规则");

    private final String code;

    private final String msg;
}
