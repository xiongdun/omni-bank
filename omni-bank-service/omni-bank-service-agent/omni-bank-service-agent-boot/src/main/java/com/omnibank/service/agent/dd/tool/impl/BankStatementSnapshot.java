package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 银行流水快照：近 12 个月经营流入/流出与期末余额
 */
public record BankStatementSnapshot(String creditCode, String dataPeriod, int months,
                                    BigDecimal operatingInflow, BigDecimal operatingOutflow,
                                    BigDecimal endingBalance, List<ToolEvidenceRef> evidences) {
}
