package com.omnibank.service.agent.dd.adapter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部数据采集结果：字段与证据中心一一对应，由适配层负责转换为证据实体
 */
public record ExternalDataItem(String evidenceType, String sourceType, String sourceSystem,
                               String sourceReference, String subjectId, String dataPeriod,
                               LocalDateTime collectedAt, String contentSummary, String contentHash,
                               BigDecimal confidence, boolean mock) {
}
