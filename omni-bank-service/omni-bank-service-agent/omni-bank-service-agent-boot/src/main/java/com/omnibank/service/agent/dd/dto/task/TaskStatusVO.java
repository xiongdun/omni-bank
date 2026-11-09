package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务状态视图：供轮询与 SSE 断线恢复使用
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskStatusVO extends BaseVO {

    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 任务状态 */
    private String taskStatus;

    /** 任务状态中文名 */
    private String taskStatusDesc;

    /** 当前执行阶段 */
    private String currentStage;

    /** 上下文版本 */
    private Integer contextVersion;

    /** 分析版本 */
    private Integer analysisVersion;

    /** 进度百分比 */
    private Integer progress;

    /** 最近错误码 */
    private String lastErrorCode;

    /** 最近错误信息 */
    private String lastErrorMessage;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
