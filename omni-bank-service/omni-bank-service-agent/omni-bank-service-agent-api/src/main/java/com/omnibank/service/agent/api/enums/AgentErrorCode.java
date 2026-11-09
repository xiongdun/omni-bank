package com.omnibank.service.agent.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 智能体域错误码：号段 230001-239999，按业务补充
 */
@Getter
@AllArgsConstructor
public enum AgentErrorCode implements ErrorCode {

    AGENT_NOT_FOUND("230001", "智能体不存在"),
    AGENT_CODE_EXISTS("230002", "智能体编码已存在"),
    AGENT_DISABLED("230003", "智能体已停用"),
    CONVERSATION_NOT_FOUND("230004", "会话不存在"),
    MESSAGE_ROLE_INVALID("230005", "消息角色非法");

    private final String code;

    private final String msg;
}
