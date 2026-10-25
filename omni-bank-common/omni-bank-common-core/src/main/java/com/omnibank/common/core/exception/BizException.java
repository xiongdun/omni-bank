package com.omnibank.common.core.exception;

import com.omnibank.common.core.result.ErrorCode;
import com.omnibank.common.core.result.GlobalErrorCode;
import java.io.Serial;
import lombok.Getter;

/**
 * 业务异常：业务规则不满足时抛出，由 web 层全局异常处理器统一转换为错误响应
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final String code;
    private final String msg;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.msg = errorCode.getMsg();
    }

    public BizException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.getCode();
        this.msg = msg;
    }

    public BizException(String code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
}
