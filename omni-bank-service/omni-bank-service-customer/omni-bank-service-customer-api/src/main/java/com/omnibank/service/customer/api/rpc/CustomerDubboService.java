package com.omnibank.service.customer.api.rpc;

/**
 * 客户域 Dubbo 服务接口（triple 协议）：其他服务跨域调用只依赖本 api 模块
 */
public interface CustomerDubboService {

    /** 探活演示：验证 Dubbo 链路 */
    String ping();
}
