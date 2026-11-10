package com.omnibank.service.agent.dd.dto.evidence;

import com.omnibank.common.core.vo.BaseVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证据详情视图：包含溯源引用与内容哈希，供 Agent 与人工复核追溯
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvidenceDetailVO extends BaseVO {

    private Long evidenceId;

    private Long taskId;

    private Long enterpriseId;

    /** 证据类型 */
    private String evidenceType;

    /** 来源类型 */
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

    /** 是否模拟数据 */
    private Boolean isMock;

    /** 证据状态 */
    private String status;
}
