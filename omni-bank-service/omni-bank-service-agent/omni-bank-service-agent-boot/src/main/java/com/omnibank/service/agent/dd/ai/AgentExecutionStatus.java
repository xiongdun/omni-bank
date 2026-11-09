package com.omnibank.service.agent.dd.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 执行状态
 */
@Getter
@AllArgsConstructor
public enum AgentExecutionStatus {

    PENDING("待执行"),
    RUNNING("执行中"),
    SUCCEEDED("执行成功"),
    FAILED("执行失败"),
    CANCELLED("已取消");

    private final String desc;
}
