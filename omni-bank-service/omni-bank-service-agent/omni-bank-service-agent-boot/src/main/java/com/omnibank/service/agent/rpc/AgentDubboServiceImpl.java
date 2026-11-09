package com.omnibank.service.agent.rpc;

import com.omnibank.service.agent.api.dto.AgentDTO;
import com.omnibank.service.agent.api.rpc.AgentDubboService;
import com.omnibank.service.agent.entity.AiAgent;
import com.omnibank.service.agent.service.agent.AgentService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;

/**
 * 智能体 Dubbo 服务实现：跨服务只读查询智能体配置
 */
@DubboService
@RequiredArgsConstructor
public class AgentDubboServiceImpl implements AgentDubboService {

    private final AgentService agentService;

    @Override
    public AgentDTO getAgent(Long agentId) {
        AiAgent agent = agentService.findById(agentId);
        if (agent == null) {
            return null;
        }
        AgentDTO dto = new AgentDTO();
        BeanUtils.copyProperties(agent, dto);
        return dto;
    }
}
