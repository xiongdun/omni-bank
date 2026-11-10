package com.omnibank.service.agent.dd.prompt;

/**
 * 渲染后的 Prompt：变量已替换，可直接提交给模型；版本信息需随结果一并记录
 */
public record RenderedPrompt(String promptCode, String version, String systemPrompt, String userPrompt) {
}
