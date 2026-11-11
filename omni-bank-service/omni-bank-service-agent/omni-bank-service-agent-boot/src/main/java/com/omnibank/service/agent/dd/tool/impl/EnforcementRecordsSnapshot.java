package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 执行类记录快照：覆盖被执行人、失信被执行人与限制高消费记录
 */
public record EnforcementRecordsSnapshot(Long evidenceId, String sourceSystem, String sourceReference,
                                         String dataPeriod, String contentSummary, boolean subjectMatchExact,
                                         String matchedSubjectName, List<EnforcementRecord> records) {

    /**
     * 单条执行记录：recordType 区分被执行人、失信被执行人与限制高消费
     */
    public record EnforcementRecord(String recordType, String caseNo, String subjectName, BigDecimal amount,
                                    LocalDate filingDate, String status, String court, String matchMode) {
    }
}
