package com.omnibank.service.risk.rpc;

import com.omnibank.service.risk.api.rpc.RiskDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 风控域 Dubbo 服务实现（triple 协议，端口 20888）
 */
@DubboService
public class RiskDubboServiceImpl implements RiskDubboService {

    @Override
    public String ping() {
        return "risk pong";
    }
}
