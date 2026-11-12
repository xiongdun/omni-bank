package com.omnibank.service.agent.dd.dto.finding;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 风险线索分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FindingQueryReq extends PageQuery {

    /** Agent 编码 */
    private String agentCode;

    /** 线索分类 */
    private String category;

    /** 严重程度 */
    private String severity;

    /** 线索状态 */
    private String status;
}
