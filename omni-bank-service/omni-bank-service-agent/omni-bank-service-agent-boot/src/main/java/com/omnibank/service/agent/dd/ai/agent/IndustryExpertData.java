package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 行业专家输出：行业分类与画像、行业基准、企业指标对比结果与证据数量
 */
@Data
public class IndustryExpertData {

    /** 国标行业代码，未识别时为 UNKNOWN */
    private String industryCode;

    /** 行业名称，未识别时为未知行业 */
    private String industryName;

    /** 市场规模（亿元） */
    private BigDecimal marketSize;

    /** 近三年行业增速（%） */
    private List<BigDecimal> growthRates;

    /** 政策导向：鼓励 / 中性 / 限制 */
    private String policyOrientation;

    /** 行业集中度 CR5（%） */
    private BigDecimal concentrationCr5;

    /** 行业周期阶段 */
    private String cycleStage;

    /** 主要风险因素 */
    private List<String> mainRisks;

    /** 行业毛利率中位数（%） */
    private BigDecimal grossMarginMedian;

    /** 行业资产负债率中位数（%） */
    private BigDecimal assetLiabilityRatioMedian;

    /** 行业应收账款周转天数中位数（天） */
    private Integer receivableTurnoverDaysMedian;

    /** 行业营收增速中位数（%） */
    private BigDecimal revenueGrowthMedian;

    /** 企业毛利率（%），上游财务分析缺失时为空 */
    private BigDecimal enterpriseGrossMargin;

    /** 企业资产负债率（%），上游财务分析缺失时为空 */
    private BigDecimal enterpriseAssetLiabilityRatio;

    /** 企业毛利率与行业中位数的差值（百分点） */
    private BigDecimal grossMarginGap;

    /** 企业资产负债率与行业中位数的差值（百分点） */
    private BigDecimal assetLiabilityGap;

    /** 行业数据期间 */
    private String dataPeriod;

    /** 可引用的证据数量 */
    private Integer evidenceCount;
}
