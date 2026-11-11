package com.omnibank.service.agent.dd.ai.agent;

/**
 * 现场调查分析输入：申报侧主体信息，为空时以任务上下文中的登记信息为准
 */
public record SiteVisitInput(String enterpriseName, String creditCode) {
}
