package com.omnibank.service.credit.outbound.customer;

import com.omnibank.service.customer.api.rpc.CustomerDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * 客户域出站端口 Dubbo 实现：@DubboReference 只允许出现在 outbound 适配实现中。
 */
@Component
public class DubboCustomerGateway implements CustomerGateway {

    @DubboReference(check = false, init = false)
    private CustomerDubboService customerDubboService;

    @Override
    public String ping() {
        return customerDubboService.ping();
    }
}
