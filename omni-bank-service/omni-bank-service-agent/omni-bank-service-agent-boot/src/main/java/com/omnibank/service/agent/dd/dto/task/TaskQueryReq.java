package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调任务分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskQueryReq extends PageQuery {

    /** 任务编号，模糊匹配 */
    private String taskNo;

    /** 任务状态 */
    private String taskStatus;

    /** 企业ID */
    private Long enterpriseId;
}
