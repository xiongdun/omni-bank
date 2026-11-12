package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.common.core.page.PageQuery;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.mapper.DdPlanMapper;
import com.omnibank.service.agent.dd.service.DdPlanService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 尽调计划查询实现：当前计划由任务表 currentPlanId 指向，历史版本按 planVersion 倒序
 */
@Service
@RequiredArgsConstructor
public class DdPlanServiceImpl extends ServiceImpl<DdPlanMapper, DdPlan> implements DdPlanService {

    private final DdTaskService taskService;

    @Override
    public DdPlan current(Long taskId) {
        DdTask task = taskService.requireTask(taskId);
        if (task.getCurrentPlanId() == null) {
            throw new BizException(DdErrorCode.PLAN_NOT_FOUND);
        }
        return requirePlan(task.getCurrentPlanId());
    }

    @Override
    public IPage<DdPlan> page(Long taskId, PageQuery query) {
        taskService.requireTask(taskId);
        return lambdaQuery()
                .eq(DdPlan::getTaskId, taskId)
                .orderByDesc(DdPlan::getPlanVersion)
                .page(new Page<>(query.getCurrent(), query.getSize()));
    }

    @Override
    public DdPlan requirePlan(Long planId) {
        DdPlan plan = getById(planId);
        if (plan == null) {
            throw new BizException(DdErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }
}
