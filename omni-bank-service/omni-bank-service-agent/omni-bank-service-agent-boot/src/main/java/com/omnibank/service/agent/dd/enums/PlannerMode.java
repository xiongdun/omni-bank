package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计划生成模式：LLM 表示由大模型生成，MOCK 表示模型未接入时由规则生成，必须在计划中显式可见
 */
@Getter
@AllArgsConstructor
public enum PlannerMode {

    LLM("大模型生成"),

    MOCK("规则生成（模型 MOCK 模式）");

    private final String desc;
}
