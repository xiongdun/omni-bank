package com.omnibank.service.agent.dd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型调用配置：模型选择通过配置管理，不写死在 Agent 代码中。
 * 模型网关地址与 API Key 只允许通过环境变量注入，禁止写入仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "omni.dd.llm")
public class DdLlmProperties {

    /** 调用模式：MOCK（缺省，不发起外部请求）/ OPENAI_COMPATIBLE（OpenAI 兼容 /chat/completions） */
    private String mode = "MOCK";

    /** 模型网关基础地址（不含 /chat/completions），如 https://model-gateway.example.com */
    private String baseUrl;

    /** API Key，仅从环境变量 OMNI_DD_LLM_API_KEY 注入 */
    private String apiKey;

    /** 模型名称，如企业网关登记的模型标识 */
    private String model;

    /** OpenAI 兼容补全路径，缺省 /v1/chat/completions */
    private String completionsPath = "/v1/chat/completions";

    /** 建立连接超时（毫秒） */
    private int connectTimeoutMs = 3000;

    /** 单次调用超时（毫秒） */
    private int requestTimeoutMs = 30000;

    /** 采样温度 */
    private double temperature = 0.2;

    /** 最大输出 Token 数 */
    private int maxTokens = 2048;
}
