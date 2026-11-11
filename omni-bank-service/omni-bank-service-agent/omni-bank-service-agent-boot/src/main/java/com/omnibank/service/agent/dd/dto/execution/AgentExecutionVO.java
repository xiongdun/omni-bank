package com.omnibank.service.agent.dd.dto.execution;

import com.omnibank.common.core.vo.BaseVO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 执行记录视图：只暴露执行元信息，不返回结果全文
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentExecutionVO extends BaseVO {

    private Long executionId;

    private Long taskId;

    private Long planId;

    private String graphCode;

    private String nodeId;

    private String agentCode;

    /** 同一节点执行序号 */
    private Integer executionNo;

    /** 执行状态 */
    private String executionStatus;

    private String summary;

    private String errorMessage;

    private String modelVersion;

    private String promptVersion;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;
}
