package com.omnibank.service.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体会话
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    /** 智能体 ID */
    private Long agentId;

    /** 会话标题 */
    private String title;

    /** 会话归属用户 ID（0 表示系统/匿名） */
    private Long userId;

    /** 状态（0 进行中 1 已结束） */
    private String status;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;
}
