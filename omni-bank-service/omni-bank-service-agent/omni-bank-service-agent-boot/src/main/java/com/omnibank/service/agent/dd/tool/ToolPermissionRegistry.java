package com.omnibank.service.agent.dd.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tool 权限边界：记录每个 Agent 声明的工具白名单，供 ToolInvoker 在调用前校验。
 * 白名单来源为 AgentDefinition.requiredTools，Agent 注册时写入，避免权限配置与 Agent 定义两处维护。
 */
@Component
public class ToolPermissionRegistry {

    private final Map<String, Set<String>> agentTools = new ConcurrentHashMap<>();

    public void register(String agentCode, List<String> toolCodes) {
        agentTools.put(agentCode, toolCodes == null ? Set.of() : Set.copyOf(toolCodes));
    }

    /** Agent 是否被允许调用该工具：未注册 Agent 或未声明的工具一律拒绝 */
    public boolean allowed(String agentCode, String toolCode) {
        Set<String> tools = agentCode == null ? null : agentTools.get(agentCode);
        return tools != null && tools.contains(toolCode);
    }
}
