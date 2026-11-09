package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.vo.BaseVO;
import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.dto.enterprise.EnterpriseVO;
import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调任务详情视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailVO extends BaseVO {

    private Long id;

    /** 任务编号 */
    private String taskNo;

    /** 任务名称 */
    private String taskName;

    /** 任务状态 */
    private String taskStatus;

    /** 任务状态中文名 */
    private String taskStatusDesc;

    /** 当前执行阶段 */
    private String currentStage;

    /** 上下文版本 */
    private Integer contextVersion;

    /** 分析版本 */
    private Integer analysisVersion;

    /** 最近错误码 */
    private String lastErrorCode;

    /** 最近错误信息 */
    private String lastErrorMessage;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 乐观锁版本 */
    private Integer version;

    /** 企业主体 */
    private EnterpriseVO enterprise;

    /** 授信申请 */
    private LoanApplicationVO application;

    /** 最新业务场景识别结果 */
    private ScenarioVO latestScenario;
}
