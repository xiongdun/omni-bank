package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 司法涉诉记录快照：工具层只做取数与标准化，风险判断由 Agent 完成
 */
public record JudicialRecordsSnapshot(Long evidenceId, String sourceSystem, String sourceReference,
                                      String dataPeriod, String contentSummary, boolean subjectMatchExact,
                                      String matchedSubjectName, List<JudicialCase> cases) {

    /**
     * 单条涉诉案件：当事人角色区分原告、被告与第三人
     */
    public record JudicialCase(String caseNo, String caseType, String role, BigDecimal amount,
                               LocalDate filingDate, LocalDate closingDate, String status, String court,
                               String subjectName, String matchMode) {
    }
}
