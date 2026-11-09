package com.omnibank.service.agent.dd.ai;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 风险线索：没有证据的结论只能标记为模型推断或待核实，AI 不得自行转为已确认
 */
@Data
public class AgentFinding {

    /** 线索编码 */
    private String findingCode;

    /** 分类 */
    private String category;

    /** 严重程度：LOW / MEDIUM / HIGH / CRITICAL / UNKNOWN */
    private String severity;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 置信度 */
    private BigDecimal confidence;

    /** 状态：CONFIRMED / NEEDS_VERIFICATION / MODEL_INFERENCE / REJECTED / RESOLVED */
    private String status;

    /** 关联证据 */
    private List<EvidenceRef> evidenceRefs;

    /** 关联的其他线索编码 */
    private List<String> relatedFindingCodes;

    /** 核实建议 */
    private String verificationSuggestion;
}
