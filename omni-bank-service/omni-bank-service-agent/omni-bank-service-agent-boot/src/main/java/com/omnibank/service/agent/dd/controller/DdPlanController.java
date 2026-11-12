package com.omnibank.service.agent.dd.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.common.core.page.PageQuery;
import com.omnibank.common.core.page.PageResult;
import com.omnibank.common.core.result.Result;
import com.omnibank.common.mybatis.util.PageResults;
import com.omnibank.service.agent.dd.dto.plan.PlanVO;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.service.DdPlanService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计划接口：计划详情与任务维度的当前计划、历史版本查询
 */
@RestController
@RequestMapping("/v1/dd")
@RequiredArgsConstructor
public class DdPlanController {

    private final DdPlanService planService;

    private final DdJsonSupport jsonSupport;

    /** 计划详情 */
    @GetMapping("/plans/{planId}")
    public Result<PlanVO> detail(@PathVariable Long planId) {
        return Result.success(toVO(planService.requirePlan(planId)));
    }

    /** 任务当前计划 */
    @GetMapping("/tasks/{taskId}/plans/current")
    public Result<PlanVO> current(@PathVariable Long taskId) {
        return Result.success(toVO(planService.current(taskId)));
    }

    /** 任务计划版本分页 */
    @GetMapping("/tasks/{taskId}/plans")
    public Result<PageResult<PlanVO>> page(@PathVariable Long taskId, PageQuery query) {
        IPage<PlanVO> page = planService.page(taskId, query).convert(this::toVO);
        return Result.success(PageResults.from(page));
    }

    private PlanVO toVO(DdPlan plan) {
        PlanVO vo = new PlanVO();
        vo.setPlanId(plan.getId());
        vo.setTaskId(plan.getTaskId());
        vo.setPlanVersion(plan.getPlanVersion());
        vo.setPlannerMode(plan.getPlannerMode());
        vo.setBusinessScenario(plan.getBusinessScenario());
        vo.setSubScenario(plan.getSubScenario());
        vo.setConfidence(plan.getConfidence());
        vo.setReasoningSummary(plan.getReasoningSummary());
        vo.setAgentPlan(jsonSupport.readStringList(plan.getAgentPlan()));
        vo.setDataPlan(readObject(plan.getDataPlan()));
        vo.setMaterialPlan(readObject(plan.getMaterialPlan()));
        vo.setDependencyPlan(jsonSupport.readStringListMap(plan.getDependencyPlan()));
        vo.setSupplementPlan(readObject(plan.getSupplementPlan()));
        vo.setExecutionConfig(readObject(plan.getExecutionConfig()));
        vo.setModelName(plan.getModelName());
        vo.setPromptVersion(plan.getPromptVersion());
        vo.setCreateTime(plan.getCreateTime());
        return vo;
    }

    private Object readObject(String json) {
        return jsonSupport.read(json, new TypeReference<Object>() {
        });
    }
}
