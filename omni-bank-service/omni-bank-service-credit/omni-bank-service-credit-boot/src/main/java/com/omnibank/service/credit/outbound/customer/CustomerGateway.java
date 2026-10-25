package com.omnibank.service.credit.outbound.customer;

/**
 * 客户域出站端口：credit 对 customer 的跨域调用统一经此端口，屏蔽具体 RPC 实现（Dubbo）。
 * 出站调用不得散落在 web/rpc 适配层（端口-适配器模式）。
 */
public interface CustomerGateway {

    /** 探活演示：验证出站链路 */
    String ping();
}
