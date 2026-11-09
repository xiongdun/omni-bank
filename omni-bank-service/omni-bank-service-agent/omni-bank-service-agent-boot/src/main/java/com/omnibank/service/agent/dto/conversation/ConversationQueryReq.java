package com.omnibank.service.agent.dto.conversation;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 会话分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationQueryReq extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 智能体 ID（精确） */
    private Long agentId;

    /** 归属用户 ID（精确） */
    private Long userId;

    /** 状态（0 进行中 1 已结束） */
    private String status;
}
