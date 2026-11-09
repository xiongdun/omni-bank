package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 证据状态：历史证据不物理删除，被新版本替代时置 SUPERSEDED
 */
@Getter
@AllArgsConstructor
public enum EvidenceStatus {

    ACTIVE("有效"),
    SUPERSEDED("已被新版本替代"),
    INVALID("已失效");

    private final String desc;
}
