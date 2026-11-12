package com.omnibank.service.agent.dd.dto.report;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告复核留痕视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportReviewVO extends BaseVO {

    private Long id;

    private String actionType;

    private String fromStatus;

    private String toStatus;

    private String fieldKey;

    private String originalValue;

    private String amendedValue;

    private String reviewer;

    private String reason;

    private String remark;

    private List<String> evidenceIds;

    private LocalDateTime createTime;
}
