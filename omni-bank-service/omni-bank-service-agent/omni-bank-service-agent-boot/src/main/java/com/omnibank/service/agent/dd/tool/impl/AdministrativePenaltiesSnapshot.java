package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 行政处罚记录快照：覆盖税务、环保与市场监管处罚
 */
public record AdministrativePenaltiesSnapshot(Long evidenceId, String sourceSystem, String sourceReference,
                                              String dataPeriod, String contentSummary, boolean subjectMatchExact,
                                              String matchedSubjectName, List<AdministrativePenaltyRecord> records) {

    /**
     * 单条行政处罚：rectified 表示是否已整改
     */
    public record AdministrativePenaltyRecord(String decisionNo, String authority, String penaltyCategory,
                                              BigDecimal amount, LocalDate decisionDate, boolean rectified,
                                              String subjectName, String matchMode) {
    }
}
