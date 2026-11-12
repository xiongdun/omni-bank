package com.omnibank.service.agent.dd.dto.report;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告版本列表视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportVersionVO extends BaseVO {

    private Long id;

    private Long taskId;

    private String reportNo;

    private Integer reportVersion;

    private String reportStatus;

    private String reviewStatus;

    private String title;

    private Integer evidenceCount;

    private Integer dataGapCount;

    private Integer supplementCount;

    private String exportStatus;

    private String modelVersion;

    private String promptVersion;

    private LocalDateTime generatedAt;
}
