package com.omnibank.service.agent.dd.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.EvidenceRef;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 上游 Agent 结果统一视图：兼容运行时对象形态与断点恢复后的 Map 形态，
 * 供综合风险与报告 Agent 读取上游结果，不再重复写类型判断。
 */
final class UpstreamAgentResults {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST =
            new TypeReference<List<Map<String, Object>>>() {
            };

    private UpstreamAgentResults() {
    }

    /** 单个上游结果快照 */
    record Snapshot(String agentCode, String status, String summary, Map<String, Object> data,
                    List<Map<String, Object>> findings, List<Map<String, Object>> dataGaps,
                    List<Map<String, Object>> supplementRequests, List<String> evidenceIds) {

        boolean succeeded() {
            return AgentExecutionStatus.SUCCEEDED.name().equals(status);
        }
    }

    static Map<String, Snapshot> collect(Map<String, Object> upstream, List<String> agentCodes,
                                         DdJsonSupport jsonSupport) {
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        for (String agentCode : agentCodes) {
            Object raw = upstream == null ? null : upstream.get(agentCode);
            snapshots.put(agentCode, of(agentCode, raw, jsonSupport));
        }
        return snapshots;
    }

    static Snapshot of(String agentCode, Object raw, DdJsonSupport jsonSupport) {
        if (raw instanceof AgentResult<?> result) {
            List<String> evidenceIds = result.getEvidenceRefs() == null ? List.of()
                    : result.getEvidenceRefs().stream()
                            .map(EvidenceRef::getEvidenceId)
                            .filter(Objects::nonNull)
                            .toList();
            return new Snapshot(agentCode, result.getStatus(), result.getSummary(),
                    toMap(jsonSupport, result.getData()),
                    toMapList(jsonSupport, result.getFindings()),
                    toMapList(jsonSupport, result.getDataGaps()),
                    toMapList(jsonSupport, result.getSupplementRequests()),
                    evidenceIds);
        }
        if (raw instanceof Map<?, ?> map) {
            Object data = map.get("data");
            return new Snapshot(agentCode, text(map.get("status")), text(map.get("summary")),
                    data instanceof Map<?, ?> dataMap ? stringKeyMap(dataMap) : Map.of(),
                    mapList(map.get("findings")), mapList(map.get("dataGaps")),
                    mapList(map.get("supplementRequests")), evidenceIds(map.get("evidenceRefs")));
        }
        return new Snapshot(agentCode, null, null, Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String dataText(Map<String, Object> data, String key) {
        return data == null ? null : text(data.get(key));
    }

    static boolean dataBoolean(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof Boolean bool && bool;
    }

    private static Map<String, Object> toMap(DdJsonSupport jsonSupport, Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return stringKeyMap(map);
        }
        return jsonSupport.readMap(jsonSupport.write(value));
    }

    private static List<Map<String, Object>> toMapList(DdJsonSupport jsonSupport, Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        if (list.get(0) instanceof Map<?, ?>) {
            List<Map<String, Object>> maps = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    maps.add(stringKeyMap(map));
                }
            }
            return List.copyOf(maps);
        }
        List<Map<String, Object>> maps = jsonSupport.read(jsonSupport.write(list), MAP_LIST);
        return maps == null ? List.of() : List.copyOf(maps);
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                maps.add(stringKeyMap(map));
            }
        }
        return List.copyOf(maps);
    }

    private static List<String> evidenceIds(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get("evidenceId");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            } else if (item != null) {
                ids.add(String.valueOf(item));
            }
        }
        return List.copyOf(ids);
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
