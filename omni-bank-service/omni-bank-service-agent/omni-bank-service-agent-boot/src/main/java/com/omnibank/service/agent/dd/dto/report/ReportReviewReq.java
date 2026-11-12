package com.omnibank.service.agent.dd.dto.report;

import com.omnibank.common.core.dto.BaseDTO;
import com.omnibank.service.agent.dd.enums.ReportReviewAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告复核请求：修正动作须传章节编码与修正后内容，驳回/退回/重新分析/修正须填写原因
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportReviewReq extends BaseDTO {

    /** 复核动作 */
    @NotNull(message = "复核动作不能为空")
    private ReportReviewAction actionType;

    /** 修正章节编码，仅修正动作使用 */
    @Size(max = 64, message = "章节编码长度不能超过 64")
    private String fieldKey;

    /** 修正后章节内容，仅修正动作使用 */
    private String amendedValue;

    /** 复核原因 */
    @Size(max = 500, message = "复核原因长度不能超过 500")
    private String reason;

    /** 复核意见 */
    @Size(max = 1000, message = "复核意见长度不能超过 1000")
    private String remark;

    /** 关联证据ID */
    private List<String> evidenceIds;
}
