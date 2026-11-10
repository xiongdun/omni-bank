package com.omnibank.service.agent.dd.dto.evidence;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证据列表视图：只暴露溯源需要的元信息，不返回内容全文
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvidenceListVO extends BaseVO {

    private Long evidenceId;

    private Long taskId;

    /** 证据类型 */
    private String evidenceType;

    /** 来源类型 */
    private String sourceType;

    /** 来源系统 */
    private String sourceSystem;

    /** 采集时间 */
    private LocalDateTime collectedAt;

    /** 是否模拟数据 */
    private Boolean isMock;

    /** 内容摘要 */
    private String contentSummary;

    /** 证据状态 */
    private String status;
}
