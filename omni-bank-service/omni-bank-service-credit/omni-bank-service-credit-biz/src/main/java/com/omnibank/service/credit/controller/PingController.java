package com.omnibank.service.credit.controller;

import com.omnibank.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探活接口：联调时验证 网关 /api/credit/ping → 本服务 /credit/ping 链路
 */
@RestController
@RequestMapping("/credit")
public class PingController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("credit pong");
    }
}
