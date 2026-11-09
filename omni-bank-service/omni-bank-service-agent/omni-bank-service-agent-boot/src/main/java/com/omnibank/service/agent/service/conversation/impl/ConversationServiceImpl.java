package com.omnibank.service.agent.service.conversation.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.context.UserContext;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.AgentErrorCode;
import com.omnibank.service.agent.dto.conversation.ConversationCreateReq;
import com.omnibank.service.agent.dto.conversation.ConversationQueryReq;
import com.omnibank.service.agent.dto.conversation.MessageAppendReq;
import com.omnibank.service.agent.entity.AiAgent;
import com.omnibank.service.agent.entity.AiConversation;
import com.omnibank.service.agent.entity.AiMessage;
import com.omnibank.service.agent.mapper.AiConversationMapper;
import com.omnibank.service.agent.service.agent.AgentService;
import com.omnibank.service.agent.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 会话服务实现（MyBatis-Plus ServiceImpl 体系）：消息表经 Db 工具类操作
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation>
        implements ConversationService {

    /** 合法消息角色 */
    private static final Set<String> MESSAGE_ROLES = Set.of("user", "assistant", "system");

    /** 智能体启用状态 */
    private static final String STATUS_ENABLED = "0";

    private final AgentService agentService;

    @Override
    public IPage<AiConversation> page(ConversationQueryReq req) {
        return lambdaQuery()
                .eq(req.getAgentId() != null, AiConversation::getAgentId, req.getAgentId())
                .eq(req.getUserId() != null, AiConversation::getUserId, req.getUserId())
                .eq(req.getStatus() != null, AiConversation::getStatus, req.getStatus())
                .orderByDesc(AiConversation::getLastMessageTime)
                .page(new Page<>(req.getCurrent(), req.getSize()));
    }

    @Override
    public AiConversation getById(Long id) {
        AiConversation conversation = super.getById(id);
        if (conversation == null) {
            throw new BizException(AgentErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    @Override
    public void create(ConversationCreateReq req) {
        AiAgent agent = agentService.getById(req.getAgentId());
        if (!STATUS_ENABLED.equals(agent.getStatus())) {
            throw new BizException(AgentErrorCode.AGENT_DISABLED);
        }
        AiConversation conversation = new AiConversation();
        conversation.setAgentId(agent.getId());
        conversation.setTitle(req.getTitle() == null || req.getTitle().isBlank()
                ? agent.getAgentName() + " 会话" : req.getTitle());
        Long userId = req.getUserId() != null ? req.getUserId() : UserContext.getUserId();
        conversation.setUserId(userId == null ? 0L : userId);
        conversation.setStatus("0");
        conversation.setLastMessageTime(LocalDateTime.now());
        save(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        removeById(id);
        Db.remove(Wrappers.<AiMessage>lambdaQuery().eq(AiMessage::getConversationId, id));
    }

    @Override
    public List<AiMessage> listMessages(Long conversationId) {
        getById(conversationId);
        return Db.list(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendMessage(Long conversationId, MessageAppendReq req) {
        getById(conversationId);
        if (!MESSAGE_ROLES.contains(req.getRole())) {
            throw new BizException(AgentErrorCode.MESSAGE_ROLE_INVALID);
        }
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(req.getRole());
        message.setContent(req.getContent());
        message.setPromptTokens(req.getPromptTokens());
        message.setCompletionTokens(req.getCompletionTokens());
        message.setLatencyMs(req.getLatencyMs());
        Db.save(message);

        AiConversation conversation = new AiConversation();
        conversation.setId(conversationId);
        conversation.setLastMessageTime(LocalDateTime.now());
        updateById(conversation);
    }
}
