package com.omnibank.service.risk.controller;

import com.omnibank.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探活接口：联调时验证 网关 /api/risk/ping → 本服务 /risk/ping 链路
 */
@RestController
@RequestMapping("/risk")
public class PingController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("risk pong");
    }
}
