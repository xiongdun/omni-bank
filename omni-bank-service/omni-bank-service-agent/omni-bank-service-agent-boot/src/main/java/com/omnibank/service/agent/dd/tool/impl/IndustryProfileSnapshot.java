package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 行业画像快照：分类口径、市场规模、增速、政策导向、集中度与周期阶段，由工具层只做标准化
 */
public record IndustryProfileSnapshot(String industryCode, String industryName, BigDecimal marketSize,
                                      List<BigDecimal> growthRates, String policyOrientation,
                                      BigDecimal concentrationCr5, String cycleStage, List<String> mainRisks,
                                      String dataPeriod, String scopeNote) {
}
