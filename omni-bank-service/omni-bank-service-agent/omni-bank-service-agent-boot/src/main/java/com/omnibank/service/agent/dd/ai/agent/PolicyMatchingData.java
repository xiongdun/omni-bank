package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * 审批政策匹配输出：制度与条款必须可回溯原文，适用性、限制条款、审批权限关注点与待人工确认项分开输出。
 * RAG 相关性不等于适用性，适用性也不等于合规结论。
 */
@Data
public class PolicyMatchingData {

    private String productCode;

    private String productName;

    private String loanPurpose;

    private String industryName;

    private BigDecimal applyAmount;

    private Integer applyTerm;

    private String termUnit;

    /** 命中的制度元数据 */
    private List<PolicySummaryItem> matchedPolicies;

    /** 条款级匹配结果 */
    private List<PolicyClauseMatch> clauseMatches;

    /** 政策检查清单 */
    private List<PolicyCheckItem> checklist;

    /** 制度冲突或适用交叉 */
    private List<String> policyConflicts;

    /** 待人工确认事项 */
    private List<String> pendingManualConfirmations;

    /** 限制条款摘要 */
    private List<String> limitations;

    /** 审批权限关注点 */
    private String approvalAuthorityFocus;

    /** 适用性说明：相关性 ≠ 适用性 ≠ 合规结论 */
    private String applicabilityNote;

    /** 合规结论边界说明 */
    private String complianceConclusionNote;

    private Integer evidenceCount;

    private List<String> missingItems;

    /** 制度元数据 */
    @Data
    public static class PolicySummaryItem {

        private String policyId;

        private String policyName;

        private String policyVersion;

        private LocalDate effectiveDate;

        private String status;
    }

    /** 条款级匹配 */
    @Data
    public static class PolicyClauseMatch {

        private String policyId;

        private String policyName;

        private String policyVersion;

        private LocalDate effectiveDate;

        private String sectionNo;

        private String clauseNo;

        private String pageNo;

        private String originalText;

        private String sourceFile;

        private String fileHash;

        private String vectorId;

        /** APPLICABLE / NEEDS_MANUAL_CONFIRMATION / NOT_APPLICABLE */
        private String applicability;

        private String applicabilityReason;

        private List<String> limitations;

        private String approvalAuthorityNote;

        private String conflict;

        private boolean needsManualConfirmation;
    }

    /** 检查清单项 */
    @Data
    public static class PolicyCheckItem {

        private String item;

        /** APPLICABLE / NEEDS_MANUAL_CONFIRMATION / NOT_APPLICABLE */
        private String status;

        private String basis;
    }
}
