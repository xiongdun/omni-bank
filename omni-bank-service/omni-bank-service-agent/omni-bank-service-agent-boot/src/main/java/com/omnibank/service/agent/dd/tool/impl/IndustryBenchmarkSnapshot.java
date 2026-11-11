package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;

/**
 * 行业基准快照：毛利率、资产负债率、应收账款周转天数与营收增速中位数，附带统计口径说明
 */
public record IndustryBenchmarkSnapshot(String industryCode, String industryName, BigDecimal grossMarginMedian,
                                        BigDecimal assetLiabilityRatioMedian, Integer receivableTurnoverDaysMedian,
                                        BigDecimal revenueGrowthMedian, String dataPeriod, String sampleNote) {
}
