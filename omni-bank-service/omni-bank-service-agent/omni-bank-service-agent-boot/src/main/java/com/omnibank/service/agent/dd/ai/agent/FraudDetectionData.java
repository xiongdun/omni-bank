package com.omnibank.service.agent.dd.ai.agent;

import com.omnibank.service.agent.dd.ai.EvidenceRef;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 反欺诈输出：每条规则命中保留规则编号、版本、命中数据、证据与人工核查结果占位。
 * 所有命中均为待核实线索，不输出欺诈认定。
 */
@Data
public class FraudDetectionData {

    /** 规则命中原样记录，人工核查结果由人工回填 */
    private List<RuleRecord> ruleRecords;

    private List<String> identityAnomalies;

    private List<String> relatedPartyAnomalies;

    private List<String> financialAnomalies;

    private List<String> transactionAnomalies;

    private List<String> fundFlowAnomalies;

    private List<String> materialAnomalies;

    /** 关联关系路径 */
    private List<String> relationPaths;

    /** 财务/税务/流水差异的可能合理原因 */
    private List<String> reasonableCauses;

    /** 风险影响说明 */
    private String riskImpact;

    /** 待人工核实事项 */
    private List<String> pendingVerificationItems;

    /** 调查建议 */
    private List<String> investigationSuggestions;

    /** 最高严重程度 */
    private String highestSeverity;

    private Integer ruleHitCount;

    /** 上游结果或基础数据缺失项 */
    private List<String> missingItems;

    private Integer evidenceCount;

    /** 单条规则命中记录 */
    @Data
    public static class RuleRecord {

        private String ruleCode;

        private String ruleVersion;

        private String category;

        private String severity;

        private String title;

        private String hitData;

        private String description;

        private String suggestion;

        private LocalDateTime hitTime;

        /** 人工核查结果，Agent 不回填 */
        private String manualVerificationResult;

        private String status;

        private List<EvidenceRef> evidenceRefs;
    }
}
