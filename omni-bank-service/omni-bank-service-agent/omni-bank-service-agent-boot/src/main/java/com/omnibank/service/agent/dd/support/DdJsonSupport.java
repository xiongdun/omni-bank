package com.omnibank.service.agent.dd.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 尽调域 JSON 字段读写：实体中的 JSON 列统一以字符串承载，出入参时在此转换
 */
@Component
@RequiredArgsConstructor
public class DdJsonSupport {

    private final ObjectMapper objectMapper;

    /** 序列化为 JSON 文本，失败直接抛业务异常，避免写入半成品数据 */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(DdErrorCode.JSON_PROCESS_FAILED);
        }
    }

    /** 读取字符串集合；空值返回空集合 */
    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(DdErrorCode.JSON_PROCESS_FAILED);
        }
    }

    /** 读取对象结构；空值返回空 Map */
    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(DdErrorCode.JSON_PROCESS_FAILED);
        }
    }
}
