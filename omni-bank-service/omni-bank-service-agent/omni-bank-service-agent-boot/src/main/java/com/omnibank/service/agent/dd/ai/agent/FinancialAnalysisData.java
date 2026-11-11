package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 财务分析输出：各期指标、指标趋势、勾稽结果、主要风险标记与缺失项说明
 */
@Data
public class FinancialAnalysisData {

    private String creditCode;

    private String enterpriseName;

    /** 取得的报表期数 */
    private Integer statementPeriods;

    /** 最新期营业收入 */
    private BigDecimal latestRevenue;

    /** 最新期净资产，供征信债务分析复用 */
    private BigDecimal latestNetAssets;

    /** 最新期净利润 */
    private BigDecimal latestNetProfit;

    /** 各期财务指标 */
    private List<FinancialPeriodIndicator> periodIndicators = new ArrayList<>();

    /** 指标趋势：指标名称 -> 趋势描述 */
    private Map<String, String> indicatorTrends = new LinkedHashMap<>();

    /** 勾稽结果：纳税申报收入、银行流水与报表收入的匹配情况 */
    private List<FinancialReconciliation> reconciliations = new ArrayList<>();

    /** 主要风险标记（Finding 编码） */
    private List<String> riskFlags = new ArrayList<>();

    /** 缺失项说明 */
    private List<String> missingItems = new ArrayList<>();

    /** 可引用证据数量 */
    private Integer evidenceCount;
}
