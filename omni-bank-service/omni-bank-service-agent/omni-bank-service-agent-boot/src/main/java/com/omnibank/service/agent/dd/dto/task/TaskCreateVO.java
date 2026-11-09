package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建任务返回
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskCreateVO extends BaseVO {

    /** 任务ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 任务状态 */
    private String taskStatus;
}
