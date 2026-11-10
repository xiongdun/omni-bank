package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务分页列表视图：只暴露前端需要的字段，不返回实体审计与逻辑删除信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskListVO extends BaseVO {

    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 任务名称 */
    private String taskName;

    /** 任务状态 */
    private String taskStatus;

    /** 任务状态中文名 */
    private String taskStatusDesc;

    /** 当前执行阶段 */
    private String currentStage;

    /** 进度百分比 */
    private Integer progress;

    /** 企业ID */
    private Long enterpriseId;

    /** 授信申请ID */
    private Long applicationId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
