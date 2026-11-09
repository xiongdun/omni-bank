package com.omnibank.common.web.config;

import com.fasterxml.jackson.databind.Module;
import com.omnibank.common.web.handler.GlobalExceptionHandler;
import com.omnibank.common.web.jackson.BaseEnumJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Web 公共组件自动装配：引依赖即生效（GlobalExceptionHandler 全局异常处理、
 * BaseEnumJacksonModule 枚举按 code 反序列化），boot 启动类无需扫描 com.omnibank.common.web。
 */
@AutoConfiguration
public class WebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Spring Boot 自动把容器内所有 Jackson Module 注册进 ObjectMapper */
    @Bean
    public Module baseEnumJacksonModule() {
        return new BaseEnumJacksonModule();
    }
}
