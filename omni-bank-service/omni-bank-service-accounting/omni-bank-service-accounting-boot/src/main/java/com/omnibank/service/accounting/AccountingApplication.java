package com.omnibank.service.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 账务域服务：会计引擎（复式记账 / 科目 / 凭证 / 总账 / 日终跑批）。
 * 当前为最小骨架：web + 注册发现（Nacos，默认关闭）；数据库/缓存/消息接入时按 application.yml 的 TODO 补充。
 */
@SpringBootApplication(scanBasePackages = {"com.omnibank.service.accounting", "com.omnibank.common.web"})
public class AccountingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingApplication.class, args);
    }
}
