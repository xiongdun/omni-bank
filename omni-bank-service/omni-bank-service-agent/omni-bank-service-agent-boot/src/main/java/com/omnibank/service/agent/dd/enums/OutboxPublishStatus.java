package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Outbox 事件发布状态
 */
@Getter
@AllArgsConstructor
public enum OutboxPublishStatus {

    PENDING("待发布"),
    PUBLISHED("已发布"),
    FAILED("发布失败");

    private final String desc;
}
