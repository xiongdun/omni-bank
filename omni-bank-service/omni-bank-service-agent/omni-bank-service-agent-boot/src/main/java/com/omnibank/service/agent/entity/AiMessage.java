package com.omnibank.service.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    /** 会话 ID */
    private Long conversationId;

    /** 角色（user 用户 / assistant 助手 / system 系统） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 耗时（毫秒） */
    private Long latencyMs;

    /** 错误信息（模型调用失败时记录） */
    private String errorMsg;
}
