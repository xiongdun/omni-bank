package com.omnibank.service.agent;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 智能体服务：智能体定义 / 会话 / 消息。
 * 业务代码统一在本模块内按 MVC 包结构组织（controller/service/mapper/entity/dto），包名区分场景。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank.service.agent")
@MapperScan(basePackages = "com.omnibank.service.agent", annotationClass = Mapper.class)
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
