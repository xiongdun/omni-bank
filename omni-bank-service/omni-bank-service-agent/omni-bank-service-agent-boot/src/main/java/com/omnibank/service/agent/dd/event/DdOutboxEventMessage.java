package com.omnibank.service.agent.dd.event;

/**
 * Outbox 事件投递到进程内监听器的消息载体：只携带定位信息，业务数据以库中 payload 为准
 */
public record DdOutboxEventMessage(String eventId, String eventType, Long aggregateId) {
}
