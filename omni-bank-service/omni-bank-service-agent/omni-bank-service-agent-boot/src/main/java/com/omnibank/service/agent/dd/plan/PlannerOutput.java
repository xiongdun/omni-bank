package com.omnibank.service.agent.dd.plan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Planner 结构化输出：对应需求文档 §4.1 的 agentPlan/dataPlan/materialPlan/dependencyPlan/supplementPlan/executionConfig
 */
@Data
public class PlannerOutput {

    /** 识别业务场景 */
    private String businessScenario;

    /** 子场景 */
    private String subScenario;

    /** 场景置信度（0-1） */
    private BigDecimal confidence;

    /** 场景状态，如 CONFIRMED_BY_MODEL / NEEDS_CONFIRMATION */
    private String scenarioStatus;

    /** 规划说明 */
    private String reasoningSummary;

    /** agentPlan：需要执行的 Agent 编码 */
    private List<String> requiredAgents = new ArrayList<>();

    /** dependencyPlan：Agent 编码 -> 直接上游 Agent 编码 */
    private Map<String, List<String>> dependencyPlan = new LinkedHashMap<>();

    /** dataPlan：需要采集的数据类型 */
    private List<String> requiredData = new ArrayList<>();

    /** materialPlan：必需材料 */
    private List<String> requiredDocuments = new ArrayList<>();

    /** materialPlan：可选材料 */
    private List<String> optionalDocuments = new ArrayList<>();

    /** supplementPlan：当前资料缺口 */
    private List<String> supplementPlan = new ArrayList<>();

    /** executionConfig：并行与执行策略 */
    private Map<String, Object> executionConfig = new LinkedHashMap<>();

    /** 不确定事项 */
    private List<String> unknowns = new ArrayList<>();
}
