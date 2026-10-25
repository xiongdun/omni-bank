package com.omnibank.common.core.result;

/**
 * 错误码约定：业务模块实现该接口定义自有错误码，配合 {@link Result} 与 {@code BizException} 使用
 */
public interface ErrorCode {

    /** 错误码 */
    String getCode();

    /** 默认提示消息 */
    String getMsg();
}
