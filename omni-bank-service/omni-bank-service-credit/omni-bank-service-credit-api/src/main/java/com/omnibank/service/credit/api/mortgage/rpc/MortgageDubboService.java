package com.omnibank.service.credit.api.mortgage.rpc;

/**
 * 抵押场景（mortgage）Dubbo 服务接口（triple 协议）：其他服务跨域调用只依赖本 api 模块
 */
public interface MortgageDubboService {

    /** 探活演示：验证 Dubbo 链路 */
    String ping();
}
