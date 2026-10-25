package com.omnibank.service.credit.controller;

import com.omnibank.common.core.result.Result;
import com.omnibank.service.customer.api.rpc.CustomerDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨域 Dubbo 调用演示：credit → customer（triple 协议）。
 * 联调需 Nacos + customer 服务在线：注入 -DDUBBO_REGISTRY_ADDRESS=nacos://127.0.0.1:8848 后启动两侧服务。
 * init=false + check=false：无注册中心时允许裸启动，调用期才初始化引用（失败由全局异常处理兜底）。
 */
@RestController
@RequestMapping("/credit")
public class RpcDemoController {

    @DubboReference(check = false, init = false)
    private CustomerDubboService customerDubboService;

    /** 验证 Dubbo 链路：网关 /api/credit/rpc-ping → credit →（Dubbo）→ customer */
    @GetMapping("/rpc-ping")
    public Result<String> rpcPing() {
        return Result.success("credit → dubbo → " + customerDubboService.ping());
    }
}
