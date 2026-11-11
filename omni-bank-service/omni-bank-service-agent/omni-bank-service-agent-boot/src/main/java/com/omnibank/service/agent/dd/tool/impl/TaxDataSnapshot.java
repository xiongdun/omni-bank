package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 纳税申报数据快照：增值税与所得税申报收入及实缴税额
 */
public record TaxDataSnapshot(String creditCode, String dataPeriod, BigDecimal declaredRevenue,
                              BigDecimal vatRevenue, BigDecimal incomeTaxRevenue,
                              BigDecimal vatPaidTax, BigDecimal incomeTaxPaidTax,
                              List<ToolEvidenceRef> evidences) {
}
