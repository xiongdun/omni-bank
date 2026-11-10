package com.omnibank.service.agent.dd.prompt;

/**
 * Prompt 模板：一个 Agent 的一个版本，包含系统提示词、用户模板与输出 Schema。
 * 模板文件按 classpath:dd/prompts/{agent 目录}/v{版本}/ 组织，历史版本只增不覆盖。
 */
public record PromptTemplate(String promptCode, String version, String systemPrompt, String userTemplate,
                             String outputSchema) {
}
