package com.omnibank.service.agent.api.rpc;

import com.omnibank.service.agent.api.dto.AgentDTO;

/**
 * 智能体 Dubbo 服务接口（triple 协议）：其他服务跨域查询智能体配置
 */
public interface AgentDubboService {

    /** 按智能体 ID 查询配置（不存在返回 null） */
    AgentDTO getAgent(Long agentId);
}
