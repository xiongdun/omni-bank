package com.omnibank.service.agent.dd.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Agent 统一输出：结构化数据 + 风险线索 + 数据缺口 + 证据引用 + 版本信息
 */
@Data
public class AgentResult<T> {

    /** Agent 编码 */
    private String agentCode;

    /** 执行ID */
    private String executionId;

    /** 执行状态 */
    private String status;

    /** 结果摘要 */
    private String summary;

    /** 结构化分析数据 */
    private T data;

    /** 风险线索 */
    private List<AgentFinding> findings;

    /** 数据缺口 */
    private List<AgentDataGap> dataGaps;

    /** 补充资料请求 */
    private List<AgentSupplementRequest> supplementRequests;

    /** 证据引用 */
    private List<EvidenceRef> evidenceRefs;

    /** 整体置信度 */
    private BigDecimal confidence;

    /** 证据完备度 */
    private BigDecimal evidenceCompleteness;

    /** 告警信息 */
    private List<String> warnings;

    /** 模型版本 */
    private String modelVersion;

    /** Prompt 版本 */
    private String promptVersion;

    /** 生成时间 */
    private LocalDateTime generatedAt;
}
