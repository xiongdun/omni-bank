package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Outbox 事件：与业务变更同事务写入，由发布器异步投递，eventId 作为消费幂等键
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_outbox_event")
public class DdOutboxEvent extends BaseEntity {

    /** 事件ID（唯一，幂等键） */
    private String eventId;

    /** 聚合类型 */
    private String aggregateType;

    /** 聚合ID */
    private Long aggregateId;

    /** 事件类型 */
    private String eventType;

    /** 事件负载（JSON） */
    private String payload;

    /** 发布状态，见 OutboxPublishStatus */
    private String publishStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 乐观锁版本 */
    private Integer version;
}
