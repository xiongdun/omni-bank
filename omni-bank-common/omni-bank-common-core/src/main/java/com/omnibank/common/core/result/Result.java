package com.omnibank.common.core.result;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体：所有 REST 接口的出参包装
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 响应码：000200 表示成功，非 000200 表示失败 */
    private String code;

    /** 响应消息 */
    private String msg;

    /** 响应数据 */
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(GlobalErrorCode.SUCCESS.getCode(), GlobalErrorCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMsg(), null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String customMsg) {
        return new Result<>(errorCode.getCode(), customMsg, null);
    }

    public static <T> Result<T> failure(String code, String msg) {
        return new Result<>(code, msg, null);
    }
}
