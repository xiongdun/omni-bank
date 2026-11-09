package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import com.omnibank.service.agent.dd.entity.DdBusinessScenario;

/**
 * 业务场景识别服务：模块一使用配置 + 规则识别，LLM 识别在 P4 Planner 接入
 */
public interface DdScenarioService {

    /** 查询最新一次识别结果，不存在抛业务异常 */
    ScenarioVO latest(Long taskId);

    /** 查询最新一次识别结果，不存在返回 null（供任务详情与上下文组装使用） */
    ScenarioVO latestOrNull(Long taskId);

    /** 执行一次识别并保存新版本（内部调用，不做状态校验） */
    ScenarioVO identify(Long taskId);

    /** 人工触发重新识别，校验任务状态 */
    ScenarioVO reidentify(Long taskId);

    /** 实体转视图 */
    ScenarioVO convert(DdBusinessScenario entity);
}
