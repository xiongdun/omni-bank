package com.omnibank.service.agent.dd.dto.plan;

import com.omnibank.common.core.vo.BaseVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调计划视图：JSON 计划字段按结构返回，便于前端直接渲染
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanVO extends BaseVO {

    private Long planId;

    private Long taskId;

    private Integer planVersion;

    private String plannerMode;

    private String businessScenario;

    private String subScenario;

    private BigDecimal confidence;

    private String reasoningSummary;

    private List<String> agentPlan;

    private Object dataPlan;

    private Object materialPlan;

    private Map<String, List<String>> dependencyPlan;

    private Object supplementPlan;

    private Object executionConfig;

    private String modelName;

    private String promptVersion;

    private LocalDateTime createTime;
}
