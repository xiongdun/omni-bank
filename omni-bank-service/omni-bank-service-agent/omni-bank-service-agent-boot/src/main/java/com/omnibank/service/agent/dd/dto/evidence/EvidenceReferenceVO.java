package com.omnibank.service.agent.dd.dto.evidence;

import com.omnibank.common.core.vo.BaseVO;
import com.omnibank.service.agent.dd.dto.finding.FindingListVO;
import com.omnibank.service.agent.dd.dto.report.ReportReviewVO;
import com.omnibank.service.agent.dd.dto.report.ReportVersionVO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证据溯源视图：一条证据被哪些线索、报告章节与复核记录引用
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvidenceReferenceVO extends BaseVO {

    private Long evidenceId;

    private Long taskId;

    private List<FindingListVO> findings;

    private List<ReportVersionVO> reports;

    private List<ReportReviewVO> reviews;
}
