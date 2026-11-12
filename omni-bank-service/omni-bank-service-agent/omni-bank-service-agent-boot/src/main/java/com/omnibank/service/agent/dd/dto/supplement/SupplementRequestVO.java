package com.omnibank.service.agent.dd.dto.supplement;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 补充材料请求视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplementRequestVO extends BaseVO {

    private Long requestId;

    private Long taskId;

    private Long planId;

    private Long executionId;

    private String agentCode;

    private String dataCode;

    private String dataName;

    private String reason;

    private String suggestedAction;

    private String status;

    private String source;

    private String remark;

    private LocalDateTime completedAt;

    private LocalDateTime createTime;
}
