package com.omnibank.service.credit;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 信贷域服务：进件申请 / 授信审批（Flowable）/ 借据与还款计划 / 额度 / 抵押 / 贷后。
 * 数据访问：MyBatis-Plus（common-mybatis），Mapper 位于各场景模块 infrastructure.persistence.mapper。
 * 线程池：omni-bank-common-threadpool 自动装配（TTL 装饰/优雅关闭），参数见 application-local.yml。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank.service.credit")
@MapperScan(basePackages = "com.omnibank.service.credit", annotationClass = Mapper.class)
public class CreditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditApplication.class, args);
    }
}
