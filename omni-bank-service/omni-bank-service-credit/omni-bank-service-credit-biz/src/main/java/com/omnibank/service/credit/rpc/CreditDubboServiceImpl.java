package com.omnibank.service.credit.rpc;

import com.omnibank.service.credit.api.rpc.CreditDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 信贷域 Dubbo 服务实现（triple 协议，端口 20883）
 */
@DubboService
public class CreditDubboServiceImpl implements CreditDubboService {

    @Override
    public String ping() {
        return "credit pong";
    }
}
