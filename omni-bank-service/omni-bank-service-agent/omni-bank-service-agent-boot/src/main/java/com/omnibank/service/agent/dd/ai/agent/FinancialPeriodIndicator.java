package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 单期财务指标：金额单位为元，比率类指标为小数（如 0.35 表示 35%）
 */
@Data
public class FinancialPeriodIndicator {

    /** 报表期间，如 2025 */
    private String period;

    private BigDecimal revenue;

    private BigDecimal operatingCashFlow;

    private BigDecimal netProfit;

    /** 营收增长率（同比） */
    private BigDecimal revenueGrowthRate;

    /** 净利润增长率（同比） */
    private BigDecimal netProfitGrowthRate;

    /** 流动比率 */
    private BigDecimal currentRatio;

    /** 速动比率 */
    private BigDecimal quickRatio;

    /** 资产负债率 */
    private BigDecimal debtAssetRatio;

    /** 毛利率 */
    private BigDecimal grossMargin;

    /** 净利率 */
    private BigDecimal netMargin;

    /** 净资产收益率 */
    private BigDecimal roe;

    /** EBITDA 利息保障倍数 */
    private BigDecimal ebitdaInterestCoverage;

    /** 经营现金流/短期债务 */
    private BigDecimal ocfShortDebtRatio;

    /** 应收账款周转天数 */
    private BigDecimal arTurnoverDays;

    /** 存货周转天数 */
    private BigDecimal inventoryTurnoverDays;
}
