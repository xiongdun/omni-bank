package com.omnibank.service.recon.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对账域错误码：号段 160001-169999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum ReconErrorCode implements ErrorCode {

    RECON_FILE_NOT_FOUND("160001", "对账文件不存在"),
    RECON_DIFF_UNRESOLVED("160002", "存在未处理的差错记录");

    private final String code;

    private final String msg;
}
