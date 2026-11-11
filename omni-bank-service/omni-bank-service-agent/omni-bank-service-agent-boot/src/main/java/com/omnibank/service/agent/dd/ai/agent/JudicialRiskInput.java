package com.omnibank.service.agent.dd.ai.agent;

/**
 * 司法风险分析输入：申报侧主体信息，为空时以任务上下文中的登记信息为准
 */
public record JudicialRiskInput(String enterpriseName, String creditCode) {
}
