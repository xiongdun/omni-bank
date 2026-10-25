package com.omnibank.service.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统设置（参数配置）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    /** 参数名称 */
    private String configName;

    /** 参数键（唯一，如 sys.user.init.password） */
    private String configKey;

    /** 参数值 */
    private String configValue;

    /** 备注 */
    private String remark;
}
