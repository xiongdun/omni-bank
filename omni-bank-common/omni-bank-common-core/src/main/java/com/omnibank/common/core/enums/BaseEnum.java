package com.omnibank.common.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 枚举基接口：业务状态类枚举实现本接口，统一 code/desc 语义。
 * 序列化：@JsonValue 使所有实现枚举 JSON 输出为 code；
 * 反序列化：common-web 自动配置按 code 匹配（BaseEnumJacksonModule）。
 */
public interface BaseEnum {

    /** 枚举码（入库/传输值） */
    @JsonValue
    Integer getCode();

    /** 枚举描述（展示值） */
    String getDesc();
}
