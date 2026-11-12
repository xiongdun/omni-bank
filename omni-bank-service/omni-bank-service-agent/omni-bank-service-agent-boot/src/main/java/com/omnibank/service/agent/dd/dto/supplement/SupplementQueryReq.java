package com.omnibank.service.agent.dd.dto.supplement;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 补充材料请求分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplementQueryReq extends PageQuery {

    /** Agent 编码 */
    private String agentCode;

    /** 请求状态，见 SupplementRequestStatus */
    private String status;

    /** 请求来源，见 SupplementRequestSource */
    private String source;
}
