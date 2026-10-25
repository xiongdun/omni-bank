package com.omnibank.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.omnibank.common.core.result.GlobalErrorCode;
import com.omnibank.common.core.result.Result;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Sentinel 网关限流配置：
 * 1. 限流/熔断触发时的兜底响应统一为 Result 结构（HTTP 429），前端按 code 统一处理
 * 2. 限流规则通过 Sentinel Dashboard 下发（路由维度 / API 分组维度），规则持久化后续接入 Nacos 数据源
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, ex) ->
                ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.failure(GlobalErrorCode.SERVICE_UNAVAILABLE, "请求过于频繁，请稍后重试")));
    }
}
