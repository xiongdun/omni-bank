package com.omnibank.service.agent.dd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.agent.dd.entity.DdAgentExecution;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 节点执行记录
 */
@Mapper
public interface DdAgentExecutionMapper extends BaseMapper<DdAgentExecution> {
}
