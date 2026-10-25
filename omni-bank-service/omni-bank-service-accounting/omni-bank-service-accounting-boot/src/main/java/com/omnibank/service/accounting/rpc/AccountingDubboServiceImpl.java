package com.omnibank.service.accounting.rpc;

import com.omnibank.service.accounting.api.rpc.AccountingDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 账务域 Dubbo 服务实现（triple 协议，端口 20885）
 */
@DubboService
public class AccountingDubboServiceImpl implements AccountingDubboService {

    @Override
    public String ping() {
        return "accounting pong";
    }
}
