package com.omnibank.service.agent.service.agent.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.AgentErrorCode;
import com.omnibank.service.agent.dto.agent.AgentCreateReq;
import com.omnibank.service.agent.dto.agent.AgentQueryReq;
import com.omnibank.service.agent.dto.agent.AgentUpdateReq;
import com.omnibank.service.agent.entity.AiAgent;
import com.omnibank.service.agent.mapper.AiAgentMapper;
import com.omnibank.service.agent.service.agent.AgentService;
import org.springframework.stereotype.Service;

/**
 * 智能体服务实现（MyBatis-Plus ServiceImpl 体系）
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AiAgentMapper, AiAgent> implements AgentService {

    @Override
    public IPage<AiAgent> page(AgentQueryReq req) {
        return lambdaQuery()
                .like(req.getAgentCode() != null, AiAgent::getAgentCode, req.getAgentCode())
                .like(req.getAgentName() != null, AiAgent::getAgentName, req.getAgentName())
                .eq(req.getStatus() != null, AiAgent::getStatus, req.getStatus())
                .orderByDesc(AiAgent::getCreateTime)
                .page(new Page<>(req.getCurrent(), req.getSize()));
    }

    @Override
    public AiAgent getById(Long id) {
        AiAgent agent = super.getById(id);
        if (agent == null) {
            throw new BizException(AgentErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    @Override
    public AiAgent findById(Long id) {
        return super.getById(id);
    }

    @Override
    public void create(AgentCreateReq req) {
        if (lambdaQuery().eq(AiAgent::getAgentCode, req.getAgentCode()).exists()) {
            throw new BizException(AgentErrorCode.AGENT_CODE_EXISTS);
        }
        AiAgent agent = new AiAgent();
        agent.setAgentCode(req.getAgentCode());
        agent.setAgentName(req.getAgentName());
        agent.setDescription(req.getDescription());
        agent.setModelName(req.getModelName());
        agent.setSystemPrompt(req.getSystemPrompt());
        agent.setTemperature(req.getTemperature());
        agent.setMaxTokens(req.getMaxTokens());
        agent.setStatus(req.getStatus());
        agent.setRemark(req.getRemark());
        save(agent);
    }

    @Override
    public void update(AgentUpdateReq req) {
        getById(req.getId());
        AiAgent agent = new AiAgent();
        agent.setId(req.getId());
        agent.setAgentName(req.getAgentName());
        agent.setDescription(req.getDescription());
        agent.setModelName(req.getModelName());
        agent.setSystemPrompt(req.getSystemPrompt());
        agent.setTemperature(req.getTemperature());
        agent.setMaxTokens(req.getMaxTokens());
        agent.setStatus(req.getStatus());
        agent.setRemark(req.getRemark());
        updateById(agent);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        removeById(id);
    }
}
