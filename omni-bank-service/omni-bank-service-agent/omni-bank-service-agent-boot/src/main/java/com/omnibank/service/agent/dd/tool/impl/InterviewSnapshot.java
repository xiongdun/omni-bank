package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;

/**
 * 管理层访谈记录快照：字段均为访谈陈述（STATEMENT），未经现场核实不得标记为已确认
 */
public record InterviewSnapshot(Long evidenceId, String sourceSystem, String sourceReference,
                                String dataPeriod, String contentSummary, String interviewDate,
                                BigDecimal capacityUtilizationRate, String orderBacklog, int employeeCount,
                                String mainCustomersStatement, String mainSuppliersStatement,
                                String interviewee, String dataNature) {
}
