package com.omnibank.service.customer.rpc;

import com.omnibank.service.customer.api.rpc.CustomerDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 客户域 Dubbo 服务实现（triple 协议，端口 20881）
 */
@DubboService
public class CustomerDubboServiceImpl implements CustomerDubboService {

    @Override
    public String ping() {
        return "customer pong";
    }
}
