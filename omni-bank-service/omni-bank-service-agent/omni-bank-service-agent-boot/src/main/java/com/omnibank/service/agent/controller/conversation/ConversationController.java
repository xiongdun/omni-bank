package com.omnibank.service.agent.controller.conversation;

import com.omnibank.common.core.page.PageResult;
import com.omnibank.common.core.result.Result;
import com.omnibank.common.mybatis.util.PageResults;
import com.omnibank.service.agent.dto.conversation.ConversationCreateReq;
import com.omnibank.service.agent.dto.conversation.ConversationQueryReq;
import com.omnibank.service.agent.dto.conversation.MessageAppendReq;
import com.omnibank.service.agent.entity.AiConversation;
import com.omnibank.service.agent.entity.AiMessage;
import com.omnibank.service.agent.service.conversation.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话管理接口
 */
@RestController
@RequestMapping("/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 分页查询会话 */
    @GetMapping("/page")
    public Result<PageResult<AiConversation>> page(ConversationQueryReq req) {
        return Result.success(PageResults.from(conversationService.page(req)));
    }

    /** 会话详情 */
    @GetMapping("/{id}")
    public Result<AiConversation> get(@PathVariable Long id) {
        return Result.success(conversationService.getById(id));
    }

    /** 新建会话 */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ConversationCreateReq req) {
        conversationService.create(req);
        return Result.success();
    }

    /** 删除会话（连带消息） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.success();
    }

    /** 查询会话消息（按时间正序） */
    @GetMapping("/{id}/messages")
    public Result<List<AiMessage>> messages(@PathVariable Long id) {
        return Result.success(conversationService.listMessages(id));
    }

    /** 追加会话消息 */
    @PostMapping("/{id}/messages")
    public Result<Void> appendMessage(@PathVariable Long id, @Valid @RequestBody MessageAppendReq req) {
        conversationService.appendMessage(id, req);
        return Result.success();
    }
}
