package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.dd.entity.DdOutboxEvent;
import com.omnibank.service.agent.dd.event.DdOutboxEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 发布器：定时扫描待发布事件并投递到进程内监听器。
 * 投递与状态更新分离，崩溃可能造成重复投递，由消费端按任务状态做幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DdOutboxPublisher {

    private static final int BATCH_SIZE = 20;

    private final DdOutboxService outboxService;

    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${omni.dd.outbox.scan-interval-ms:1000}")
    public void publishPending() {
        for (DdOutboxEvent event : outboxService.fetchPending(BATCH_SIZE)) {
            try {
                eventPublisher.publishEvent(
                        new DdOutboxEventMessage(event.getEventId(), event.getEventType(), event.getAggregateId()));
                outboxService.markPublished(event);
                log.info("尽调事件发布成功 eventId={}, eventType={}, aggregateId={}",
                        event.getEventId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("尽调事件发布失败 eventId={}, eventType={}, aggregateId={}",
                        event.getEventId(), event.getEventType(), event.getAggregateId(), e);
                outboxService.markFailed(event);
            }
        }
    }
}
