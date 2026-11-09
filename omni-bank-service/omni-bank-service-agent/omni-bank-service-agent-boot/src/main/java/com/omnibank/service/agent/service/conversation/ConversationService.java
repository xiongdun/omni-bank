package com.omnibank.service.agent.service.conversation;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.omnibank.service.agent.dto.conversation.ConversationCreateReq;
import com.omnibank.service.agent.dto.conversation.ConversationQueryReq;
import com.omnibank.service.agent.dto.conversation.MessageAppendReq;
import com.omnibank.service.agent.entity.AiConversation;
import com.omnibank.service.agent.entity.AiMessage;

import java.util.List;

/**
 * 会话服务：会话分页/创建/删除 + 会话消息查询与追加
 */
public interface ConversationService extends IService<AiConversation> {

    /** 分页查询会话 */
    IPage<AiConversation> page(ConversationQueryReq req);

    /** 按 ID 查询会话（不存在时抛业务异常） */
    AiConversation getById(Long id);

    /** 新建会话（校验智能体存在且启用） */
    void create(ConversationCreateReq req);

    /** 删除会话（连带逻辑删除消息） */
    void delete(Long id);

    /** 按时间正序查询会话消息 */
    List<AiMessage> listMessages(Long conversationId);

    /** 追加一条会话消息并刷新会话最后消息时间 */
    void appendMessage(Long conversationId, MessageAppendReq req);
}
