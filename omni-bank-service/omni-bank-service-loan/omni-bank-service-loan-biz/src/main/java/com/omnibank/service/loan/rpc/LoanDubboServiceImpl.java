package com.omnibank.service.loan.rpc;

import com.omnibank.service.loan.api.rpc.LoanDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 借据域 Dubbo 服务实现（triple 协议，端口 20884）
 */
@DubboService
public class LoanDubboServiceImpl implements LoanDubboService {

    @Override
    public String ping() {
        return "loan pong";
    }
}
