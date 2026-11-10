package com.omnibank.service.agent.dd.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 显式 Mock 的 Spring AI ChatModel：不发起任何外部请求，返回固定 JSON，供模型网关未确认前验证链路。
 * Mock 结果必须在业务侧可见（mock=true），不得冒充真实模型输出。
 */
@Slf4j
@RequiredArgsConstructor
public class MockChatModel implements ChatModel {

    public static final String MODEL_NAME = "MOCK_CHAT_MODEL";

    private final ObjectMapper objectMapper;

    private final AtomicBoolean warned = new AtomicBoolean(false);

    @Override
    public ChatResponse call(Prompt prompt) {
        if (warned.compareAndSet(false, true)) {
            log.warn("大模型调用处于 MOCK 模式（omni.dd.llm.mode=MOCK），返回结果仅用于链路验证");
        }
        ObjectNode content = objectMapper.createObjectNode();
        content.put("mock", true);
        content.put("model", MODEL_NAME);
        content.put("answer", "MOCK_LLM_RESPONSE");
        content.put("prompt", prompt == null ? "" : prompt.getContents());
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content.toString()))));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().model(MODEL_NAME).build();
    }
}
