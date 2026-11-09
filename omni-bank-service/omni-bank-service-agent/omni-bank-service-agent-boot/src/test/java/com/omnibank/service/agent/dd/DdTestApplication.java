package com.omnibank.service.agent.dd;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 尽调域集成测试启动类：只扫描 dd 包，不加载 Nacos / Dubbo 外部依赖
 */
@SpringBootApplication(scanBasePackages = "com.omnibank.service.agent.dd")
@MapperScan(basePackages = "com.omnibank.service.agent.dd.mapper", annotationClass = Mapper.class)
public class DdTestApplication {
}
