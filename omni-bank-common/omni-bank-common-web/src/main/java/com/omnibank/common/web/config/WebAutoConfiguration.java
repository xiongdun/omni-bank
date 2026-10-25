package com.omnibank.common.web.config;

import com.omnibank.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Web 公共组件自动装配：引依赖即生效（GlobalExceptionHandler 全局异常处理），
 * boot 启动类无需扫描 com.omnibank.common.web。
 */
@AutoConfiguration
public class WebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
