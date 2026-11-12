package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 补充材料请求来源：Agent 自动提出或客户经理手工创建
 */
@Getter
@AllArgsConstructor
public enum SupplementRequestSource {

    AGENT("Agent 提出"),
    MANUAL("手工创建");

    private final String desc;
}
