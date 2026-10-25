package com.omnibank.service.accounting.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账务域错误码：号段 140001-149999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum AccountingErrorCode implements ErrorCode {

    ENTRY_NOT_BALANCED("140001", "借贷分录不平衡"),
    VOUCHER_DUPLICATED("140002", "会计凭证重复");

    private final String code;

    private final String msg;
}
