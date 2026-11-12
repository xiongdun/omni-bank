package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 综合风险评估输出：只做跨 Agent 汇总、去重、维度评估与风险缓释建议，
 * 不输出风险等级、最终审批金额与合规结论。
 */
@Data
public class ComprehensiveRiskAssessmentData {

    /** 是否具备完整评估条件：关键上游（财务、征信、用途）全部成功才为 true */
    private boolean assessable;

    /** 不具备完整评估条件的原因 */
    private List<String> notAssessableReasons;

    /** 风险等级固定 NOT_RATED：未提供本行正式评级标准，不臆造等级 */
    private String riskLevel;

    /** 风险等级说明 */
    private String riskLevelNote;

    /** 综合风险摘要 */
    private String riskSummary;

    /** 9 个维度的评估结果 */
    private List<DimensionAssessment> dimensions;

    /** 去重后的重大风险线索 */
    private List<MajorRisk> majorRisks;

    /** 跨 Agent 风险关联：同一事实被多个 Agent 发现时合并并记录关系 */
    private List<RiskRelation> riskRelations;

    /** 上游原始风险线索数量 */
    private int rawFindingCount;

    /** 去重后风险线索数量 */
    private int deduplicatedFindingCount;

    /** 数据缺口数量 */
    private int dataGapCount;

    /** 第一还款来源分析 */
    private String primaryRepaymentAnalysis;

    /** 第二还款来源分析 */
    private String secondaryRepaymentAnalysis;

    /** 担保情况分析 */
    private String guaranteeAnalysis;

    /** 风险缓释建议 */
    private List<String> mitigationSuggestions;

    /** 金额与期限关注事项，不输出审批金额 */
    private List<String> amountAndTermConcerns;

    /** 数据完整度：上游成功 Agent 数 / 10 */
    private BigDecimal dataCompleteness;

    /** 评估可信程度 */
    private BigDecimal assessmentConfidence;

    /** 完整度说明 */
    private String completenessDetail;

    /** 未成功执行的上游 Agent */
    private List<String> missingAgents;

    /** 缺失项说明 */
    private List<String> missingItems;

    /** 引用证据数量 */
    private int evidenceCount;

    /** AI 边界说明 */
    private String approvalNote;

    /** 单维度评估结果 */
    @Data
    public static class DimensionAssessment {

        /** 维度编码 */
        private String dimension;

        /** 维度名称 */
        private String dimensionName;

        /** 该维度关联的上游 Agent */
        private List<String> agentCodes;

        /** 成功执行的上游 Agent 数 */
        private int succeededAgentCount;

        /** 风险线索数量（去重后） */
        private int signalCount;

        /** 最高严重程度 */
        private String highestSeverity;

        /** 维度状态：SUFFICIENT / PARTIAL / UNAVAILABLE */
        private String status;

        /** 维度说明 */
        private String description;

        /** 维度引用证据数量 */
        private int evidenceCount;
    }

    /** 重大风险线索（去重后） */
    @Data
    public static class MajorRisk {

        /** 线索编码，取首个来源 Agent 的编码 */
        private String findingCode;

        /** 分类 */
        private String category;

        /** 严重程度 */
        private String severity;

        /** 标题 */
        private String title;

        /** 描述 */
        private String description;

        /** 状态固定 NEEDS_VERIFICATION */
        private String status;

        /** 来源 Agent */
        private List<String> sourceAgents;

        /** 合并的其他线索编码 */
        private List<String> relatedFindingCodes;

        /** 关联证据 ID */
        private List<String> evidenceIds;
    }

    /** 跨 Agent 风险关联 */
    @Data
    public static class RiskRelation {

        /** 关系类型 */
        private String relationType;

        /** 关联 Agent */
        private List<String> agents;

        /** 关联线索编码 */
        private List<String> findingCodes;

        /** 关系说明 */
        private String description;
    }
}
