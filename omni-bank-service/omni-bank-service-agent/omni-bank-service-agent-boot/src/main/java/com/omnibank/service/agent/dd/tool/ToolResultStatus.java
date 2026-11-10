package com.omnibank.service.agent.dd.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tool 结果状态：统一表达成功、部分成功、未找到与失败
 */
@Getter
@AllArgsConstructor
public enum ToolResultStatus {

    SUCCESS("成功"),
    PARTIAL_SUCCESS("部分成功"),
    NOT_FOUND("未找到"),
    FAILED("失败");

    private final String desc;
}
