package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告人工复核记录：每次复核动作留痕，修正动作保留原始值、修正值、修正人、原因与关联证据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_report_review")
public class DdReportReview extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 报告ID */
    private Long reportId;

    /** 报告版本 */
    private Integer reportVersion;

    /** 复核动作：SUBMIT / CONFIRM / SUPPLEMENT / AMEND / REJECT / REQUEST_MATERIAL / REANALYZE / FINALIZE */
    private String actionType;

    /** 动作前报告状态 */
    private String fromStatus;

    /** 动作后报告状态 */
    private String toStatus;

    /** 修正字段（章节编码），仅修正动作有值 */
    private String fieldKey;

    /** 修正前内容 */
    private String originalValue;

    /** 修正后内容 */
    private String amendedValue;

    /** 复核人 */
    private String reviewer;

    /** 复核原因（驳回、退回、重新分析、修正时必填） */
    private String reason;

    /** 复核意见 */
    private String remark;

    /** 关联证据ID（JSON 数组） */
    private String evidenceIds;
}
