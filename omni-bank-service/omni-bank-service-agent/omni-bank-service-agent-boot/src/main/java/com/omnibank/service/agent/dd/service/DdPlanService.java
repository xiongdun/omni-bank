package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.common.core.page.PageQuery;
import com.omnibank.service.agent.dd.entity.DdPlan;

/**
 * 尽调计划查询服务：当前计划、历史版本与计划详情
 */
public interface DdPlanService {

    /** 任务当前计划，任务无计划时抛业务异常 */
    DdPlan current(Long taskId);

    /** 任务计划版本分页，按版本倒序 */
    IPage<DdPlan> page(Long taskId, PageQuery query);

    /** 按计划ID查询，不存在抛业务异常 */
    DdPlan requirePlan(Long planId);
}
