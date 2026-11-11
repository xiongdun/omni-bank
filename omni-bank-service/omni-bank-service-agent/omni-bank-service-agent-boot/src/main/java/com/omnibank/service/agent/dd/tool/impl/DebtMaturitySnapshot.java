package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 债务期限结构快照：短/中/长期有息负债与未来 6/12 个月到期金额
 */
public record DebtMaturitySnapshot(String creditCode, BigDecimal shortTermDebt, BigDecimal midTermDebt,
                                   BigDecimal longTermDebt, BigDecimal totalInterestBearingDebt,
                                   BigDecimal dueWithin6Months, BigDecimal dueWithin12Months,
                                   List<ToolEvidenceRef> evidences) {
}
