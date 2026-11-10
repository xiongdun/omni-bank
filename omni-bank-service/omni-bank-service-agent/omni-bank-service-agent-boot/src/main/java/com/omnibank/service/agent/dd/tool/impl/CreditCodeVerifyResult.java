package com.omnibank.service.agent.dd.tool.impl;

/**
 * 统一社会信用代码校验结果
 */
public record CreditCodeVerifyResult(boolean valid, String reason) {
}
