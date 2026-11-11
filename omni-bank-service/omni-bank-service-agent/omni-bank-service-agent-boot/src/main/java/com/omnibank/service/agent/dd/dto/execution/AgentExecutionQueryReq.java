package com.omnibank.service.agent.dd.dto.execution;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 执行记录分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentExecutionQueryReq extends PageQuery {

    /** 执行状态 */
    private String executionStatus;

    /** 节点ID */
    private String nodeId;
}
