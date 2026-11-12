package com.omnibank.service.agent.dd.dto.report;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告详情视图：包含章节内容与引用证据
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportDetailVO extends ReportVersionVO {

    /** 章节内容（chapterNo / chapterCode / title / content / status / evidenceIds / dataGaps / limitation） */
    private List<Map<String, Object>> chapters;

    /** 引用证据ID */
    private List<String> evidenceIds;
}
