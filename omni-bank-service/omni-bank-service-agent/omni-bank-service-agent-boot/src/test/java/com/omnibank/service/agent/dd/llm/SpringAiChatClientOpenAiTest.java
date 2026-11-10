package com.omnibank.service.agent.dd.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.config.DdChatClientConfiguration;
import com.omnibank.service.agent.dd.config.DdLlmProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Spring AI ChatClient + OpenAI 兼容协议测试：用本地 HttpServer 验证请求/响应与错误码转换，不访问真实模型网关。
 */
class SpringAiChatClientOpenAiTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    private final AtomicReference<String> authorization = new AtomicReference<>();

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private final AtomicReference<String> requestPath = new AtomicReference<>();

    private volatile int responseStatus = 200;

    private volatile String responseContent = "测试回答";

    private DdChatService chatService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = completionBody(responseContent).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        DdLlmProperties properties = new DdLlmProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        ChatModel chatModel = new DdChatClientConfiguration().openAiChatModel(properties);
        chatService = new DdChatService(ChatClient.create(chatModel));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 调用OpenAi兼容接口时携带鉴权与模型参数() {
        String content = chatService.complete("系统提示", "用户提示");

        assertThat(content).isEqualTo("测试回答");
        assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get()).contains("\"model\":\"test-model\"")
                .contains("用户提示")
                .contains("系统提示");
    }

    @Test
    void 模型网关返回服务端错误时转换为调用失败错误码() {
        responseStatus = 500;

        assertThatThrownBy(() -> chatService.complete("系统提示", "用户提示"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230123"));
    }

    @Test
    void 模型输出不是合法JSON时转换为输出解析失败错误码() {
        responseContent = "not-json";

        assertThatThrownBy(() -> chatService.completeForEntity("系统提示", "用户提示",
                new ParameterizedTypeReference<Map<String, Object>>() {
                }))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230124"));
    }

    @Test
    void 结构化输出由SpringAi完成反序列化() {
        responseContent = "{\"mock\":true,\"model\":\"test-model\"}";

        Map<String, Object> payload = chatService.completeForEntity("系统提示", "用户提示",
                new ParameterizedTypeReference<Map<String, Object>>() {
                });

        assertThat(payload).containsEntry("mock", true).containsEntry("model", "test-model");
    }

    @Test
    void OPENAI_COMPATIBLE模式缺少必要配置时启动失败() {
        DdLlmProperties properties = new DdLlmProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setApiKey("test-key");

        assertThatThrownBy(() -> new DdChatClientConfiguration().openAiChatModel(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model");
    }

    private String completionBody(String content) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "id", "chatcmpl-test",
                    "object", "chat.completion",
                    "created", 1700000000,
                    "model", "test-model",
                    "choices", new Object[]{
                            Map.of("index", 0,
                                    "message", Map.of("role", "assistant", "content", content),
                                    "finish_reason", "stop")
                    },
                    "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
