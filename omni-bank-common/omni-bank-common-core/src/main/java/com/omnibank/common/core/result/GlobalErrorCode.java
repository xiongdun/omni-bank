package com.omnibank.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局通用错误码：6 位号段，前两位 00 为通用域；000200 成功，0004xx 客户端侧错误，0005xx 服务端侧错误。
 * 业务模块专属错误码在各自模块中实现 {@link ErrorCode} 扩展（使用各自域号段），不在此堆叠。
 */
@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    SUCCESS("000200", "成功"),
    PARAM_ERROR("000400", "请求参数错误"),
    UNAUTHORIZED("000401", "未认证或凭证已失效"),
    FORBIDDEN("000403", "无访问权限"),
    NOT_FOUND("000404", "资源不存在"),
    DUPLICATE_REQUEST("000409", "请勿重复提交"),
    SYSTEM_ERROR("000500", "系统内部错误"),
    SERVICE_UNAVAILABLE("000503", "服务暂不可用，请稍后重试");

    private final String code;

    private final String msg;
}
