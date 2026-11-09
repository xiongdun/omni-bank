package com.omnibank.service.agent.dd.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务场景识别规则配置：产品编码 / 贷款用途关键词 -> 业务类型。
 * 规则未命中或置信度低于阈值时只标记待确认，不做推断性分类。
 */
@Data
@Component
@ConfigurationProperties(prefix = "omni.dd.scenario")
public class DdScenarioProperties {

    /** 置信度阈值：低于该值的识别结果需人工确认 */
    private BigDecimal confidenceThreshold = new BigDecimal("0.60");

    /** 规则未命中时的默认置信度 */
    private BigDecimal unmatchedConfidence = new BigDecimal("0.30");

    /** 识别规则 */
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {

        /** 规则名称（写入识别明细，便于追溯） */
        private String name;

        /** 产品编码集合 */
        private List<String> productCodes = new ArrayList<>();

        /** 贷款用途关键词集合（包含匹配） */
        private List<String> purposeKeywords = new ArrayList<>();

        /** 业务类型，取值见 BusinessType */
        private String businessType;

        /** 子场景标识 */
        private String subScenario;

        /** 规则命中时的置信度 */
        private BigDecimal confidence;
    }
}
