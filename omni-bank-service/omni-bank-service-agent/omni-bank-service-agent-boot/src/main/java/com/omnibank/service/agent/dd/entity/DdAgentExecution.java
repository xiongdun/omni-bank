package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 节点执行记录：每次节点执行落一行，恢复执行时按最新成功记录复用结果
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_agent_execution")
public class DdAgentExecution extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 计划ID，未关联计划的直接调度为空 */
    private Long planId;

    /** Graph 编码 */
    private String graphCode;

    /** 节点ID */
    private String nodeId;

    /** Agent 编码，汇聚节点为空 */
    private String agentCode;

    /** 同一任务同一节点的执行序号，恢复重跑递增 */
    private Integer executionNo;

    /** 执行状态，见 GraphNodeStatus */
    private String executionStatus;

    /** 结果摘要 */
    private String summary;

    /** 失败或跳过原因 */
    private String errorMessage;

    /** 节点输入快照（JSON） */
    private String inputSnapshot;

    /** AgentResult 结果快照（JSON） */
    private String resultContent;

    /** 模型版本 */
    private String modelVersion;

    /** Prompt 版本 */
    private String promptVersion;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 乐观锁版本 */
    private Integer version;
}
