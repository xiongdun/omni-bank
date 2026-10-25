package com.omnibank.service.payment.rpc;

import com.omnibank.service.payment.api.rpc.PaymentDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 支付域 Dubbo 服务实现（triple 协议，端口 20886）
 */
@DubboService
public class PaymentDubboServiceImpl implements PaymentDubboService {

    @Override
    public String ping() {
        return "payment pong";
    }
}
