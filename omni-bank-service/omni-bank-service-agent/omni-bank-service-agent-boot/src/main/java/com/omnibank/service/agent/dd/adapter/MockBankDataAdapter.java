package com.omnibank.service.agent.dd.adapter;

import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 银行/外部数据 Mock 适配器：真实接口未确认前用于打通证据链路。
 * 所有数据的来源系统以 MOCK_ 开头、isMock=true，内容摘要带【MOCK】前缀，禁止被误当成真实数据。
 * 通过 omni.dd.adapter.mock.enabled=false 可关闭（关闭后任务初始化不会自动采集基础证据）。
 */
@Component
@ConditionalOnProperty(prefix = "omni.dd.adapter.mock", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MockBankDataAdapter implements ExternalDataAdapter {

    private static final String ENTERPRISE_REGISTRY = "MOCK_ENTERPRISE_REGISTRY";

    private static final String JUDICIAL_PLATFORM = "MOCK_JUDICIAL_PLATFORM";

    private static final String CREDIT_BUREAU = "MOCK_CREDIT_BUREAU";

    @Override
    public String name() {
        return "MOCK_BANK_DATA";
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public List<ExternalDataItem> collect(ExternalDataQuery query) {
        LocalDateTime collectedAt = LocalDateTime.now();
        LocalDate today = collectedAt.toLocalDate();
        return List.of(
                item(EvidenceType.ENTERPRISE_PROFILE, EvidenceSourceType.EXTERNAL_DATA, ENTERPRISE_REGISTRY,
                        "MOCK-ENTERPRISE-REGISTRY:" + query.creditCode(),
                        "【MOCK】企业名称=" + query.enterpriseName() + "；统一社会信用代码=" + query.creditCode()
                                + "；法定代表人=" + query.legalPerson() + "；行业=" + query.industryName()
                                + "；经营状态=存续（模拟数据，仅用于链路验证）",
                        query, today.toString(), new BigDecimal("0.90"), collectedAt),
                item(EvidenceType.JUDICIAL_RECORD, EvidenceSourceType.EXTERNAL_DATA, JUDICIAL_PLATFORM,
                        "MOCK-JUDICIAL-PLATFORM:" + query.creditCode(),
                        "【MOCK】未查询到涉诉记录（模拟数据，仅用于链路验证）",
                        query, today.toString(), new BigDecimal("0.85"), collectedAt),
                item(EvidenceType.CREDIT_REPORT, EvidenceSourceType.BANK_INTERFACE, CREDIT_BUREAU,
                        "MOCK-CREDIT-BUREAU:" + query.creditCode(),
                        "【MOCK】征信报告模拟数据：无逾期记录，近 6 个月查询次数 0（仅用于链路验证）",
                        query, currentQuarter(today), new BigDecimal("0.80"), collectedAt));
    }

    private ExternalDataItem item(EvidenceType evidenceType, EvidenceSourceType sourceType, String sourceSystem,
                                  String sourceReference, String summary, ExternalDataQuery query,
                                  String dataPeriod, BigDecimal confidence, LocalDateTime collectedAt) {
        return new ExternalDataItem(evidenceType.name(), sourceType.name(), sourceSystem, sourceReference,
                query.creditCode(), dataPeriod, collectedAt, summary, sha256(summary), confidence, true);
    }

    private String currentQuarter(LocalDate date) {
        return date.getYear() + "Q" + ((date.getMonthValue() - 1) / 3 + 1);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
