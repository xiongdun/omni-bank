package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 补充材料请求状态：PENDING 可完成，COMPLETED 已触发增量重分析
 */
@Getter
@AllArgsConstructor
public enum SupplementRequestStatus {

    PENDING("待补充"),
    COMPLETED("已补充"),
    CANCELLED("已取消");

    private final String desc;
}
