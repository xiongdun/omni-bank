package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.dd.entity.DdOutboxEvent;
import java.util.List;

/**
 * Outbox 事件服务：与业务变更同事务写入，由发布器异步投递
 */
public interface DdOutboxService {

    /** 追加事件（必须在业务事务内调用） */
    void append(String aggregateType, Long aggregateId, String eventType, Object payload);

    /** 拉取待发布事件（含到达重试时间的失败事件） */
    List<DdOutboxEvent> fetchPending(int limit);

    /** 标记发布成功 */
    void markPublished(DdOutboxEvent event);

    /** 标记本次发布失败，按退避策略安排重试 */
    void markFailed(DdOutboxEvent event);
}
