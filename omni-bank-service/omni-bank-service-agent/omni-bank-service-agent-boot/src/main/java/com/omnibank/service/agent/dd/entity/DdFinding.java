package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调风险线索：由 Agent 节点结果中的 findings 落库，按任务 + Agent 维护当前线索集
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_finding")
public class DdFinding extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 计划ID */
    private Long planId;

    /** Agent 执行记录ID */
    private Long executionId;

    /** Agent 编码 */
    private String agentCode;

    /** Graph 节点ID */
    private String nodeId;

    /** 线索编码 */
    private String findingCode;

    /** 线索分类 */
    private String category;

    /** 严重程度：LOW / MEDIUM / HIGH / CRITICAL / UNKNOWN */
    private String severity;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 置信度 */
    private BigDecimal confidence;

    /** 线索状态：CONFIRMED / NEEDS_VERIFICATION / MODEL_INFERENCE / REJECTED / RESOLVED */
    private String status;

    /** 关联证据引用（JSON） */
    private String evidenceRefs;

    /** 关联线索编码（JSON） */
    private String relatedFindingCodes;

    /** 核实建议 */
    private String verificationSuggestion;

    /** 乐观锁版本 */
    private Integer version;
}
