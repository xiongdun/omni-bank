package com.omnibank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * omni-bank API 网关：统一对外接口入口。
 * 职责：路由转发（Nacos 服务发现）、限流熔断（Sentinel）、统一跨域；后续扩展鉴权过滤、灰度路由。
 */
@SpringBootApplication
public class OmniBankGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmniBankGatewayApplication.class, args);
    }
}
