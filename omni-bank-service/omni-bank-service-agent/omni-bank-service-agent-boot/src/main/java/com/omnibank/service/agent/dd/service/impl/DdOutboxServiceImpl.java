package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.service.agent.dd.entity.DdOutboxEvent;
import com.omnibank.service.agent.dd.enums.OutboxPublishStatus;
import com.omnibank.service.agent.dd.mapper.DdOutboxEventMapper;
import com.omnibank.service.agent.dd.service.DdOutboxService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Outbox 事件服务实现：状态更新带条件，避免多实例并发重复发布
 */
@Service
public class DdOutboxServiceImpl extends ServiceImpl<DdOutboxEventMapper, DdOutboxEvent>
        implements DdOutboxService {

    /** 失败重试的最大次数 */
    private final int maxRetries;

    private final DdJsonSupport jsonSupport;

    public DdOutboxServiceImpl(DdJsonSupport jsonSupport,
                               @Value("${omni.dd.outbox.max-retries:5}") int maxRetries) {
        this.jsonSupport = jsonSupport;
        this.maxRetries = maxRetries;
    }

    @Override
    public void append(String aggregateType, Long aggregateId, String eventType, Object payload) {
        DdOutboxEvent event = new DdOutboxEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(jsonSupport.write(payload));
        event.setPublishStatus(OutboxPublishStatus.PENDING.name());
        event.setRetryCount(0);
        event.setVersion(0);
        save(event);
    }

    @Override
    public List<DdOutboxEvent> fetchPending(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery()
                .eq(DdOutboxEvent::getPublishStatus, OutboxPublishStatus.PENDING.name())
                .and(w -> w.isNull(DdOutboxEvent::getNextRetryAt).or().le(DdOutboxEvent::getNextRetryAt, now))
                .orderByAsc(DdOutboxEvent::getId)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    public void markPublished(DdOutboxEvent event) {
        lambdaUpdate()
                .eq(DdOutboxEvent::getId, event.getId())
                .eq(DdOutboxEvent::getPublishStatus, OutboxPublishStatus.PENDING.name())
                .set(DdOutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHED.name())
                .set(DdOutboxEvent::getPublishedAt, LocalDateTime.now())
                .set(DdOutboxEvent::getVersion, event.getVersion() + 1)
                .update();
    }

    @Override
    public void markFailed(DdOutboxEvent event) {
        int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        boolean exhausted = retryCount >= maxRetries;
        LocalDateTime nextRetryAt = exhausted ? null : LocalDateTime.now().plusSeconds(Math.min(60L * retryCount, 300L));
        lambdaUpdate()
                .eq(DdOutboxEvent::getId, event.getId())
                .eq(DdOutboxEvent::getPublishStatus, OutboxPublishStatus.PENDING.name())
                .set(DdOutboxEvent::getPublishStatus,
                        exhausted ? OutboxPublishStatus.FAILED.name() : OutboxPublishStatus.PENDING.name())
                .set(DdOutboxEvent::getRetryCount, retryCount)
                .set(DdOutboxEvent::getNextRetryAt, nextRetryAt)
                .set(DdOutboxEvent::getVersion, event.getVersion() + 1)
                .update();
    }
}
