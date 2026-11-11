package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;

/**
 * 财务勾稽结果：reportedValue 为本行报表口径，comparedValue 为税务或流水口径
 */
public record FinancialReconciliation(String itemCode, String itemName, BigDecimal reportedValue,
                                      BigDecimal comparedValue, BigDecimal deviationRate, boolean consistent,
                                      String description) {
}
