package com.omnibank.service.agent.dd.tool;

import java.util.Map;

/**
 * Tool 调用请求：taskId 用于数据权限与证据归属，params 为工具入参
 */
public record ToolRequest(Long taskId, Map<String, Object> params) {

    public ToolRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public String stringParam(String name) {
        Object value = params.get(name);
        return value == null ? null : String.valueOf(value);
    }
}
