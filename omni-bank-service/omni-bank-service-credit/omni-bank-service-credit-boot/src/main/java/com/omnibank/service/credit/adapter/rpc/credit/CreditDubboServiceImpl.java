package com.omnibank.service.credit.adapter.rpc.credit;

import com.omnibank.service.credit.api.credit.rpc.CreditDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 授信场景 Dubbo 适配层（triple 协议，注册于 credit 服务名下）。
 * ping 为契约级探活，不过应用服务；真实业务方法后续委托 ApplicationService。
 */
@DubboService
public class CreditDubboServiceImpl implements CreditDubboService {

    /** 契约级探活：验证 RPC 链路 */
    @Override
    public String ping() {
        return "credit pong";
    }
}
