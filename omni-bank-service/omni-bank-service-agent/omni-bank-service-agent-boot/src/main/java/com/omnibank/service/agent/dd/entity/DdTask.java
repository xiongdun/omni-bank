package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业尽调任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_task")
public class DdTask extends BaseEntity {

    /** 任务编号（唯一） */
    private String taskNo;

    /** 企业ID */
    private Long enterpriseId;

    /** 授信申请ID */
    private Long applicationId;

    /** 任务名称 */
    private String taskName;

    /** 任务状态，见 TaskStatus */
    private String taskStatus;

    /** 当前执行阶段 */
    private String currentStage;

    /** 当前计划ID（P4 Planner 接入后使用） */
    private Long currentPlanId;

    /** 上下文版本 */
    private Integer contextVersion;

    /** 分析版本 */
    private Integer analysisVersion;

    /** 最近错误码 */
    private String lastErrorCode;

    /** 最近错误信息 */
    private String lastErrorMessage;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 乐观锁版本 */
    private Integer version;
}
