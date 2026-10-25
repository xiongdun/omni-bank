package com.omnibank.service.credit.adapter.web;

import com.omnibank.common.core.result.Result;
import com.omnibank.service.credit.outbound.customer.CustomerGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨域 Dubbo 调用演示：credit → customer（triple 协议）。仅 demo profile 加载，生产包不含演示代码。
 * 出站调用经 {@link CustomerGateway} 端口封装，适配层不直连下游 RPC。
 * 联调：SPRING_PROFILES_ACTIVE=demo,local + -DDUBBO_REGISTRY_ADDRESS=nacos://127.0.0.1:8848 + customer 在线。
 */
@RestController
@RequestMapping("/credit")
@Profile("demo")
@RequiredArgsConstructor
public class RpcDemoController {

    private final CustomerGateway customerGateway;

    /** 验证 Dubbo 链路：网关 /api/credit/rpc-ping → credit →（Dubbo）→ customer */
    @GetMapping("/rpc-ping")
    public Result<String> rpcPing() {
        return Result.success("credit → dubbo → " + customerGateway.ping());
    }
}
