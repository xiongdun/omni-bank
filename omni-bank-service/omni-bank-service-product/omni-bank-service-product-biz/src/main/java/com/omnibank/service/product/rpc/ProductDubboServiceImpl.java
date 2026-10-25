package com.omnibank.service.product.rpc;

import com.omnibank.service.product.api.rpc.ProductDubboService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 产品域 Dubbo 服务实现（triple 协议，端口 20882）
 */
@DubboService
public class ProductDubboServiceImpl implements ProductDubboService {

    @Override
    public String ping() {
        return "product pong";
    }
}
