package com.omnibank.service.agent.dd.ai;

import lombok.Data;

/**
 * 补充资料请求：由 Agent 提出、客户经理执行，触发受影响 Agent 增量重分析
 */
@Data
public class AgentSupplementRequest {

    /** 数据编码 */
    private String dataCode;

    /** 数据名称 */
    private String dataName;

    /** 请求原因 */
    private String reason;

    /** 建议补充方式 */
    private String suggestedAction;
}
