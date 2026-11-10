package com.omnibank.service.agent.dd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibank.service.agent.dd.llm.MockChatModel;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * Spring AI 模型装配：业务侧只使用 ChatClient，不直接依赖模型供应商协议。
 * 模式由 omni.dd.llm.mode 显式选择，缺省 MOCK，不与真实调用静默切换。
 */
@Configuration
public class DdChatClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "omni.dd.llm", name = "mode", havingValue = "MOCK", matchIfMissing = true)
    public ChatModel mockChatModel(ObjectMapper objectMapper) {
        return new MockChatModel(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "omni.dd.llm", name = "mode", havingValue = "OPENAI_COMPATIBLE")
    public ChatModel openAiChatModel(DdLlmProperties properties) {
        if (isBlank(properties.getBaseUrl()) || isBlank(properties.getApiKey())
                || isBlank(properties.getModel())) {
            throw new IllegalStateException(
                    "OPENAI_COMPATIBLE 模式需要配置 omni.dd.llm.base-url / api-key / model，密钥请通过环境变量 OMNI_DD_LLM_API_KEY 注入");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1, properties.getRequestTimeoutMs()));
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .apiKey(properties.getApiKey())
                .completionsPath(properties.getCompletionsPath())
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                // 关闭框架默认重试，避免模型网关异常时长时间阻塞；重试策略由后续编排层统一控制
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Bean
    public ChatClient ddChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
