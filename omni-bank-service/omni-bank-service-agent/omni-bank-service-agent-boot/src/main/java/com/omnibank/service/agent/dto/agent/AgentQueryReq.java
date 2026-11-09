package com.omnibank.service.agent.dto.agent;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 智能体分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentQueryReq extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 智能体编码（模糊） */
    private String agentCode;

    /** 智能体名称（模糊） */
    private String agentName;

    /** 状态（0 正常 1 停用） */
    private String status;
}
