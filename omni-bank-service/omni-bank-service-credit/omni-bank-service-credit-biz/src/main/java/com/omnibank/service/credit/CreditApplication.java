package com.omnibank.service.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 信贷域服务：进件申请 / 授信审批（Flowable）/ 借据与还款计划。
 * 当前为最小骨架：web + 注册发现（Nacos，默认关闭）；数据库/缓存/消息接入时按 application.yml 的 TODO 补充。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank")
public class CreditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditApplication.class, args);
    }
}
