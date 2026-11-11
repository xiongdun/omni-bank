package com.omnibank.service.agent.dd.support;

import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 业务域 Mock 证据构造器：统一 isMock 标记、内容哈希与采集时间，避免各工具重复实现
 */
public final class DdMockEvidence {

    private DdMockEvidence() {
    }

    /**
     * 构造一条 Mock 证据。
     *
     * @param sourceReference 来源内稳定引用（同一任务重复执行应保持一致，用于幂等）
     */
    public static ExternalDataItem item(EvidenceType evidenceType, EvidenceSourceType sourceType,
                                        String sourceSystem, String sourceReference, String subjectId,
                                        String dataPeriod, String contentSummary, BigDecimal confidence) {
        return new ExternalDataItem(evidenceType.name(), sourceType.name(), sourceSystem, sourceReference,
                subjectId, dataPeriod, LocalDateTime.now(), contentSummary, sha256(contentSummary),
                confidence, true);
    }

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
