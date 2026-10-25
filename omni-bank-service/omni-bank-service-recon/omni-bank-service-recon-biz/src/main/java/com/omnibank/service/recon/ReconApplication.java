package com.omnibank.service.recon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 对账域服务：渠道/账务对账中心（T+1 文件解析、差错处理）。
 * 当前为最小骨架：web + 注册发现（Nacos，默认关闭）；数据库/缓存/消息接入时按 application.yml 的 TODO 补充。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank")
public class ReconApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconApplication.class, args);
    }
}
