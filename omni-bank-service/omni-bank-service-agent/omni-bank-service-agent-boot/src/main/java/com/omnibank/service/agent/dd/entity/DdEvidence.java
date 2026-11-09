package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调证据索引：模块一只保存证据元信息，原始文件与解析结果在 P2 补齐
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_evidence")
public class DdEvidence extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 证据类型，见 EvidenceType */
    private String evidenceType;

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

    /** 证据状态，见 EvidenceStatus */
    private String status;

    /** 乐观锁版本 */
    private Integer version;
}
