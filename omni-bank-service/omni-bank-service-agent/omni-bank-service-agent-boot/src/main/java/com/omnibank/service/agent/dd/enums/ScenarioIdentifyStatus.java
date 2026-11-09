package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务场景识别状态：低置信度或规则未命中必须落 NEEDS_CONFIRMATION，不得伪造识别结论
 */
@Getter
@AllArgsConstructor
public enum ScenarioIdentifyStatus {

    IDENTIFIED("已识别"),
    NEEDS_CONFIRMATION("待人工确认"),
    CONFIRMED_BY_USER("人工已确认"),
    FAILED("识别失败");

    private final String desc;
}
