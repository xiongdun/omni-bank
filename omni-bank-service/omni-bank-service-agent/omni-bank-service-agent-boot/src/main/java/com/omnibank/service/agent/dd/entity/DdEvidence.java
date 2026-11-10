package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调证据索引：保存证据元信息，原始文件与解析结果在后续阶段补齐
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_evidence")
public class DdEvidence extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 企业ID */
    private Long enterpriseId;

    /** 证据类型，见 EvidenceType */
    private String evidenceType;

    /** 来源类型，见 EvidenceSourceType */
    private String sourceType;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源引用 */
    private String sourceReference;

    /** 主体标识 */
    private String subjectId;

    /** 数据期间 */
    private String dataPeriod;

    /** 采集时间 */
    private LocalDateTime collectedAt;

    /** 内容摘要 */
    private String contentSummary;

    /** 内容哈希 */
    private String contentHash;

    /** 证据置信度 */
    private BigDecimal confidence;

    /** 是否模拟数据：银行/外部接口未确认前由 Mock Adapter 采集，必须显式标记 */
    private Boolean isMock;

    /** 证据状态，见 EvidenceStatus */
    private String status;

    /** 乐观锁版本 */
    private Integer version;
}
