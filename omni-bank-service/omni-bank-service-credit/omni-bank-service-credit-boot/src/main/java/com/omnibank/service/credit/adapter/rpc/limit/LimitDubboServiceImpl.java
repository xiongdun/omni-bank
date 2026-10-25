package com.omnibank.service.credit.adapter.rpc.limit;

import com.omnibank.service.credit.api.limit.rpc.LimitDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 额度场景 Dubbo 适配层（triple 协议，注册于 credit 服务名下）。
 * ping 为契约级探活，不过应用服务；真实业务方法后续委托 ApplicationService。
 */
@DubboService
public class LimitDubboServiceImpl implements LimitDubboService {

    /** 契约级探活：验证 RPC 链路 */
    @Override
    public String ping() {
        return "limit pong";
    }
}
