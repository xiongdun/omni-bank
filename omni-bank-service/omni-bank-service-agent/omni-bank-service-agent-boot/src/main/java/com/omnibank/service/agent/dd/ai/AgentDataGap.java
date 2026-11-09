package com.omnibank.service.agent.dd.ai;

import lombok.Data;

/**
 * 数据缺口：接口失败、客户未提供与业务不适用必须区分，不能把数据缺失等同于风险异常
 */
@Data
public class AgentDataGap {

    /** 数据编码 */
    private String dataCode;

    /** 数据名称 */
    private String dataName;

    /** 缺口说明 */
    private String description;

    /** 是否必需 */
    private boolean required;

    /** 来源类型 */
    private String sourceType;

    /** 状态：REQUIRED / OPTIONAL / UNAVAILABLE / NOT_PROVIDED / NOT_APPLICABLE / UNKNOWN */
    private String status;

    /** 对分析的影响 */
    private String impact;

    /** 建议动作 */
    private String suggestedAction;
}
