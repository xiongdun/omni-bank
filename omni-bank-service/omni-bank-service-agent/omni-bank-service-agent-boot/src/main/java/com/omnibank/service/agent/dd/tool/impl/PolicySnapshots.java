package com.omnibank.service.agent.dd.tool.impl;

import java.time.LocalDate;
import java.util.List;

/**
 * 政策匹配工具快照：检索结果只返回制度元数据，条款结果必须带原文、页码、版本与来源文件
 */
public final class PolicySnapshots {

    private PolicySnapshots() {
    }

    /** 制度元数据 */
    public record PolicySummary(String policyId, String policyName, String policyVersion, LocalDate effectiveDate,
                                LocalDate expiryDate, String status, String sourceFile, String fileHash) {
    }

    /** 制度条款 */
    public record Clause(String policyId, String policyName, String policyVersion, LocalDate effectiveDate,
                         LocalDate expiryDate, String status, String sectionNo, String clauseNo, String pageNo,
                         String originalText, String sourceFile, String fileHash, String vectorId,
                         String approvalAuthorityNote) {
    }

    /** 检索结果 */
    public record SearchResult(List<PolicySummary> policies, String query, String dataPeriod) {
    }

    /** 条款检索结果 */
    public record ClauseResult(String policyId, List<Clause> clauses, String dataPeriod) {
    }
}
