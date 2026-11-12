package com.omnibank.service.agent.dd.dto.finding;

import com.omnibank.common.core.vo.BaseVO;
import com.omnibank.service.agent.dd.ai.EvidenceRef;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 风险线索视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FindingListVO extends BaseVO {

    private Long findingId;

    private Long taskId;

    private Long planId;

    private Long executionId;

    private String agentCode;

    private String nodeId;

    private String findingCode;

    private String category;

    private String severity;

    private String title;

    private String description;

    private BigDecimal confidence;

    private String status;

    private List<EvidenceRef> evidenceRefs;

    private List<String> relatedFindingCodes;

    private String verificationSuggestion;

    private LocalDateTime createTime;
}
