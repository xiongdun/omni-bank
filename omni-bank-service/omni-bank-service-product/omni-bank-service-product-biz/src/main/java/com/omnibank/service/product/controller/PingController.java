package com.omnibank.service.product.controller;

import com.omnibank.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探活接口：联调时验证 网关 /api/product/ping → 本服务 /product/ping 链路
 */
@RestController
@RequestMapping("/product")
public class PingController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("product pong");
    }
}
