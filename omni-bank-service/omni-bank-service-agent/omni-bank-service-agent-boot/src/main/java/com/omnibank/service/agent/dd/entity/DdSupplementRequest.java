package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调补充材料请求：Agent 提出或客户经理手工创建，完成后触发受影响 Agent 增量重分析
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_supplement_request")
public class DdSupplementRequest extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 计划ID */
    private Long planId;

    /** Agent 执行记录ID */
    private Long executionId;

    /** 提出请求的 Agent 编码，手工创建可为空 */
    private String agentCode;

    /** 数据编码 */
    private String dataCode;

    /** 数据名称 */
    private String dataName;

    /** 请求原因 */
    private String reason;

    /** 建议补充方式 */
    private String suggestedAction;

    /** 请求状态，见 SupplementRequestStatus */
    private String status;

    /** 请求来源，见 SupplementRequestSource */
    private String source;

    /** 完成说明 */
    private String remark;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 乐观锁版本 */
    private Integer version;
}
