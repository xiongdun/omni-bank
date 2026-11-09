package com.omnibank.service.agent.dd.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 任务进度 SSE 连接注册表：仅承载实时推送，不落库；断线后由状态查询接口恢复
 */
@Slf4j
@Component
public class DdSseRegistry {

    /** 连接超时 30 分钟 */
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(e -> remove(taskId, emitter));
        return emitter;
    }

    /** 推送事件，发送失败的连接直接移除 */
    public void publish(Long taskId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(taskId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 推送失败并移除连接 taskId={}", taskId);
                remove(taskId, emitter);
            }
        }
    }

    private void remove(Long taskId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(taskId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(taskId);
        }
    }
}
