package com.omnibank.service.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 产品域服务：信贷产品工厂（期限/利率/还款方式/计息规则配置化）。
 * 当前为最小骨架：web + 注册发现（Nacos，默认关闭）；数据库/缓存/消息接入时按 application.yml 的 TODO 补充。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank")
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
