package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 尽调计划：按 planVersion 版本化保存，历史版本不覆盖，任务表 currentPlanId 指向当前版本
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_plan")
public class DdPlan extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 计划版本（任务内自增） */
    private Integer planVersion;

    /** 计划生成模式，见 PlannerMode */
    private String plannerMode;

    /** 识别业务场景 */
    private String businessScenario;

    /** 子场景 */
    private String subScenario;

    /** 场景置信度 */
    private BigDecimal confidence;

    /** 规划说明 */
    private String reasoningSummary;

    /** Agent 计划（JSON） */
    private String agentPlan;

    /** 数据采集计划（JSON） */
    private String dataPlan;

    /** 材料计划（JSON） */
    private String materialPlan;

    /** Agent 依赖关系（JSON） */
    private String dependencyPlan;

    /** 资料缺口计划（JSON） */
    private String supplementPlan;

    /** 执行配置（JSON） */
    private String executionConfig;

    /** 模型名称（规则生成时为 MOCK_RULE） */
    private String modelName;

    /** Prompt 版本 */
    private String promptVersion;

    /** 乐观锁版本 */
    private Integer version;
}
