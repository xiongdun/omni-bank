package com.omnibank.service.system;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统管理域服务：用户/角色/菜单权限/数据权限/机构部门/岗位/字典/系统设置/日志/通知/附件。
 * 业务代码统一在本模块内按 MVC 包结构组织（controller/service/mapper/entity/dto），包名区分场景。
 */
@SpringBootApplication(scanBasePackages = "com.omnibank.service.system")
@MapperScan(basePackages = "com.omnibank.service.system", annotationClass = Mapper.class)
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
