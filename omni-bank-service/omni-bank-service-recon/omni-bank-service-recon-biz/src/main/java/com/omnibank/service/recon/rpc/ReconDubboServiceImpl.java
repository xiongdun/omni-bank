package com.omnibank.service.recon.rpc;

import com.omnibank.service.recon.api.rpc.ReconDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 对账域 Dubbo 服务实现（triple 协议，端口 20887）
 */
@DubboService
public class ReconDubboServiceImpl implements ReconDubboService {

    @Override
    public String ping() {
        return "recon pong";
    }
}
