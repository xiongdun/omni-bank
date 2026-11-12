package com.omnibank.service.agent.api.enums;

import com.omnibank.common.core.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对公信贷智能尽调域错误码：延续智能体域 230001-239999 号段，尽调子段 230101-230199
 */
@Getter
@AllArgsConstructor
public enum DdErrorCode implements ErrorCode {

    TASK_NOT_FOUND("230101", "尽调任务不存在"),
    TASK_STATUS_NOT_ALLOWED("230102", "任务状态不允许当前操作"),
    TASK_STATUS_CONFLICT("230103", "任务状态已被并发修改，请刷新后重试"),
    TASK_SUBMIT_INCOMPLETE("230104", "任务提交条件不满足"),
    TASK_NOT_CANCELLABLE("230105", "任务当前状态不可取消"),
    ENTERPRISE_IDENTITY_MISSING("230106", "企业主体标识缺失"),
    ENTERPRISE_NOT_FOUND("230107", "企业主体不存在"),
    ENTERPRISE_CONFLICT("230108", "企业主体并发创建冲突"),
    LOAN_APPLICATION_NOT_FOUND("230109", "授信申请不存在"),
    SCENARIO_NOT_FOUND("230110", "业务场景识别结果不存在"),
    SCENARIO_IDENTIFY_FAILED("230111", "业务场景识别失败"),
    CONTEXT_ASSEMBLE_FAILED("230112", "企业上下文组装失败"),
    OUTBOX_EVENT_PUBLISH_FAILED("230113", "Outbox 事件发布失败"),
    JSON_PROCESS_FAILED("230114", "JSON 数据处理失败"),
    EVIDENCE_NOT_FOUND("230115", "证据不存在"),
    EVIDENCE_COLLECT_FAILED("230116", "证据采集失败"),
    TOOL_NOT_FOUND("230117", "工具不存在"),
    AGENT_NOT_FOUND("230118", "Agent 不存在"),
    AGENT_EXECUTION_FAILED("230119", "Agent 执行失败"),
    PROMPT_NOT_FOUND("230120", "Prompt 模板不存在"),
    PROMPT_RENDER_FAILED("230121", "Prompt 渲染失败"),
    GRAPH_INVALID("230122", "Graph 定义非法"),
    LLM_CALL_FAILED("230123", "大模型调用失败"),
    LLM_OUTPUT_INVALID("230124", "大模型输出解析失败"),
    GRAPH_EXECUTION_FAILED("230125", "Graph 执行失败"),
    PLANNER_GENERATE_FAILED("230126", "尽调计划生成失败"),
    PLAN_INVALID("230127", "尽调计划校验未通过"),
    TASK_NOT_RESUMABLE("230128", "任务当前状态不可恢复执行"),
    REPORT_NOT_FOUND("230129", "尽调报告不存在"),
    REPORT_STATUS_NOT_ALLOWED("230130", "报告当前状态不允许该复核操作"),
    REPORT_CHAPTER_NOT_FOUND("230131", "报告章节不存在"),
    REPORT_CONFLICT("230132", "报告已被并发修改，请刷新后重试"),
    REPORT_REVIEW_PARAM_INVALID("230133", "复核参数不完整或非法");

    private final String code;

    private final String msg;
}
