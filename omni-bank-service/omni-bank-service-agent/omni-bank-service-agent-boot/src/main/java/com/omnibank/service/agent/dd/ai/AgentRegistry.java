package com.omnibank.service.agent.dd.ai;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.tool.ToolPermissionRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Agent 注册表：按 agentCode 发现 Agent，重复注册在启动期直接失败；
 * 注册时把 AgentDefinition.requiredTools 写入 Tool 权限边界，工具调用前统一校验。
 */
@Component
public class AgentRegistry {

    private final Map<String, CreditAgent<?, ?>> agents = new LinkedHashMap<>();

    public AgentRegistry(List<CreditAgent<?, ?>> agentList, ToolPermissionRegistry permissionRegistry) {
        for (CreditAgent<?, ?> agent : agentList) {
            if (agents.putIfAbsent(agent.getAgentCode(), agent) != null) {
                throw new IllegalStateException("Agent 编码重复注册：" + agent.getAgentCode());
            }
            permissionRegistry.register(agent.getAgentCode(), agent.getDefinition().getRequiredTools());
        }
    }

    public CreditAgent<?, ?> require(String agentCode) {
        CreditAgent<?, ?> agent = agents.get(agentCode);
        if (agent == null) {
            throw new BizException(DdErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    /** 已注册 Agent 编码，按注册顺序返回 */
    public List<String> codes() {
        return List.copyOf(agents.keySet());
    }

    /** 编码是否已注册：Graph 调度用于标记未交付 Agent，避免把未交付误判为执行失败 */
    public boolean contains(String agentCode) {
        return agentCode != null && agents.containsKey(agentCode);
    }
}
