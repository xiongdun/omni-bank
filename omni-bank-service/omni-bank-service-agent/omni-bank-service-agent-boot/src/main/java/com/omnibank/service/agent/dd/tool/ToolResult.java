package com.omnibank.service.agent.dd.tool;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Tool 统一输出：无论成功失败都返回结构化结果，调用方不需要捕获底层异常
 */
@Data
public class ToolResult<T> {

    private boolean success;

    private ToolResultStatus status;

    private T data;

    /** 数据来源，如 MOCK_ENTERPRISE_REGISTRY */
    private String source;

    /** 查询时间 */
    private LocalDateTime queryTime;

    private String errorCode;

    private String errorMessage;

    /** 本次调用产生的证据ID */
    private List<String> evidenceIds;

    private Map<String, Object> metadata;

    public static <T> ToolResult<T> success(String source, T data, List<String> evidenceIds,
                                            Map<String, Object> metadata) {
        ToolResult<T> result = new ToolResult<>();
        result.setSuccess(true);
        result.setStatus(ToolResultStatus.SUCCESS);
        result.setSource(source);
        result.setData(data);
        result.setEvidenceIds(evidenceIds == null ? List.of() : evidenceIds);
        result.setMetadata(metadata == null ? Map.of() : metadata);
        result.setQueryTime(LocalDateTime.now());
        return result;
    }

    public static <T> ToolResult<T> notFound(String source, String message) {
        ToolResult<T> result = failure(source, null, message);
        result.setStatus(ToolResultStatus.NOT_FOUND);
        return result;
    }

    public static <T> ToolResult<T> failure(String source, String errorCode, String message) {
        ToolResult<T> result = new ToolResult<>();
        result.setSuccess(false);
        result.setStatus(ToolResultStatus.FAILED);
        result.setSource(source);
        result.setErrorCode(errorCode);
        result.setErrorMessage(message);
        result.setEvidenceIds(List.of());
        result.setMetadata(Map.of());
        result.setQueryTime(LocalDateTime.now());
        return result;
    }
}
