package com.omnibank.service.agent.dto.conversation;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 会话创建请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationCreateReq extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 智能体 ID */
    @NotNull(message = "智能体 ID 不能为空")
    private Long agentId;

    /** 会话标题 */
    private String title;

    /** 会话归属用户 ID（不传取当前登录用户） */
    private Long userId;
}
