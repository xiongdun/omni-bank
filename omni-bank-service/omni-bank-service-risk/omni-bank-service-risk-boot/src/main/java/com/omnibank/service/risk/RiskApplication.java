package com.omnibank.service.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 风控域服务：决策引擎（规则/评分卡/决策流）/ 名单服务。
 * 当前为最小骨架：web + 注册发现（Nacos，默认关闭）；数据库/缓存/消息接入时按 application.yml 的 TODO 补充。
 * 后续规划：决策引擎独立部署时由本服务拆出，目标 P99 < 100ms。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank.service.risk")
public class RiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskApplication.class, args);
    }
}
