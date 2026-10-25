package com.omnibank.service.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 客户域服务：客户/KYC/实名信息管理。
 * 当前为最小骨架：web + 注册发现（Nacos，默认关闭）；数据库/缓存/消息接入时按 application.yml 的 TODO 补充。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank")
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
