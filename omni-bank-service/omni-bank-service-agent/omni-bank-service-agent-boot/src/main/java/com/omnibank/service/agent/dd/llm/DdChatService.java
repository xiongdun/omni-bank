package com.omnibank.service.agent.dd.llm;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 业务侧模型调用入口：封装 Spring AI ChatClient，统一转换调用与输出解析异常为业务错误码。
 * 结构化输出由 Spring AI BeanOutputConverter 校验，业务代码不直接解析模型原文。
 */
@Component
@RequiredArgsConstructor
public class DdChatService {

    private final ChatClient chatClient;

    /**
     * 纯文本补全；调用失败返回 230123
     */
    public String complete(String systemPrompt, String userPrompt) {
        try {
            return request(systemPrompt).user(userPrompt).call().content();
        } catch (RuntimeException e) {
            throw new BizException(DdErrorCode.LLM_CALL_FAILED, "大模型调用失败：" + rootMessage(e));
        }
    }

    /**
     * 结构化输出；模型调用或输出解析失败均返回 230124，避免未校验的模型 JSON 进入业务
     */
    public <T> T completeForEntity(String systemPrompt, String userPrompt,
                                   ParameterizedTypeReference<T> outputType) {
        try {
            return request(systemPrompt).user(userPrompt).call().entity(outputType);
        } catch (RuntimeException e) {
            throw new BizException(DdErrorCode.LLM_OUTPUT_INVALID, "大模型输出解析失败：" + rootMessage(e));
        }
    }

    private ChatClient.ChatClientRequestSpec request(String systemPrompt) {
        ChatClient.ChatClientRequestSpec request = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            request.system(systemPrompt);
        }
        return request;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
