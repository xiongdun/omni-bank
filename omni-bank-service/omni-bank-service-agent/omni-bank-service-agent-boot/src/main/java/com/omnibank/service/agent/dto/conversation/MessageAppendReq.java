package com.omnibank.service.agent.dto.conversation;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 会话消息追加请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageAppendReq extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色（user 用户 / assistant 助手 / system 系统） */
    @NotBlank(message = "消息角色不能为空")
    private String role;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 耗时（毫秒） */
    private Long latencyMs;
}
