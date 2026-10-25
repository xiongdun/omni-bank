package com.omnibank.common.web.handler;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.common.core.result.GlobalErrorCode;
import com.omnibank.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将异常统一转换为 {@link Result} 响应。
 * 约定：服务应用启动类需位于 com.omnibank 包下，使本模块 Bean 被组件扫描。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按错误码原样返回，warn 级别记录 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        return Result.failure(e.getCode(), e.getMsg());
    }

    /** 参数校验异常：返回首个字段校验错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? GlobalErrorCode.PARAM_ERROR.getMsg() : fieldError.getDefaultMessage();
        log.warn("参数校验失败: {}", msg);
        return Result.failure(GlobalErrorCode.PARAM_ERROR, msg);
    }

    /** 兜底异常：不向前端暴露内部细节，error 级别记录堆栈 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.failure(GlobalErrorCode.SYSTEM_ERROR);
    }
}
