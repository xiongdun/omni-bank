package com.omnibank.service.agent.dd.ai;

/**
 * 专业 Agent 统一抽象：P1 只落地契约，12 个 Agent 的分析逻辑在后续阶段实现。
 * 执行线程、重试与持久化由 Runtime / Graph 负责，Agent 自身只做单次分析。
 */
public interface CreditAgent<I, O> {

    /** Agent 编码，见各 Agent 规格，如 SUBJECT_VERIFICATION */
    String getAgentCode();

    /** Agent 名称 */
    String getAgentName();

    /** Agent 定义（输入输出 Schema、依赖、所需工具等） */
    AgentDefinition getDefinition();

    /** 执行一次分析，必须返回结构化结果并关联证据 */
    AgentResult<O> execute(AgentContext context, I input);
}
