package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调报告版本：同一任务每次生成新增一行，历史版本只增不覆盖
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_due_diligence_report")
public class DdDueDiligenceReport extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 报告编号 */
    private String reportNo;

    /** 报告版本，同一任务递增 */
    private Integer reportVersion;

    /** 报告状态：AI_DRAFT_COMPLETE / AI_DRAFT_INCOMPLETE */
    private String reportStatus;

    /** 复核状态：PENDING_MANUAL_REVIEW / REVIEWED */
    private String reviewStatus;

    /** 报告标题 */
    private String title;

    /** 报告章节内容（JSON） */
    private String contentJson;

    /** 报告引用的证据ID集合（JSON） */
    private String evidenceIds;

    /** 引用证据数量 */
    private Integer evidenceCount;

    /** 数据缺口数量 */
    private Integer dataGapCount;

    /** 补充材料请求数量 */
    private Integer supplementCount;

    /** 导出状态，P7 固定 NOT_AVAILABLE */
    private String exportStatus;

    /** 模型版本 */
    private String modelVersion;

    /** Prompt 版本 */
    private String promptVersion;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 乐观锁版本 */
    private Integer version;
}
