package com.omnibank.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消息发送封装。
 * 注意：应用需配置 rocketmq.name-server 与 rocketmq.producer.group，否则启动失败。
 * TODO: 后续按需扩展事务消息（Outbox 模式）与延迟消息封装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketMessageSender {

    private final RocketMQTemplate rocketMQTemplate;

    /** 同步发送到指定主题 */
    public void send(String topic, Object payload) {
        rocketMQTemplate.syncSend(topic, payload);
        log.info("MQ 消息发送成功: topic={}", topic);
    }

    /** 同步发送到指定主题与 Tag（destination 格式为 topic:tag） */
    public void send(String topic, String tag, Object payload) {
        rocketMQTemplate.syncSend(topic + ":" + tag, payload);
        log.info("MQ 消息发送成功: topic={}, tag={}", topic, tag);
    }
}
