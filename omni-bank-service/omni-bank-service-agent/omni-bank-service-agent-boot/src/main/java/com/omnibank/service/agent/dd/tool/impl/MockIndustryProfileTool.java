package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.support.DdMockEvidence;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 行业画像查询工具（Mock）：按行业名称/经营范围/企业名称关键字匹配国标行业并返回画像。
 * 行业数据来自 Mock 行业研究库，未匹配时返回 UNKNOWN 且不登记证据，避免把未知行业包装成事实。
 */
@Component
@RequiredArgsConstructor
public class MockIndustryProfileTool implements AgentTool<IndustryProfileSnapshot> {

    public static final String TOOL_CODE = "industry.getIndustryProfile";

    private static final String SOURCE_SYSTEM = "MOCK_INDUSTRY_PROVIDER";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.70");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "行业画像查询（Mock）",
                "返回国标行业分类、市场规模、近三年增速、政策导向、集中度与周期阶段，数据为 Mock 行业研究",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<IndustryProfileSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        IndustryProfileSnapshot profile = MockIndustryCatalog.resolveProfile(
                request.stringParam("industryName"), request.stringParam("businessScope"),
                request.stringParam("enterpriseName"));
        if (MockIndustryCatalog.UNKNOWN_CODE.equals(profile.industryCode())) {
            return ToolResult.success(SOURCE_SYSTEM, profile, List.of(),
                    Map.of("mock", true, "resolved", false));
        }

        String summary = "Mock 行业研究：" + profile.industryName() + "（" + profile.industryCode()
                + "），市场规模 " + profile.marketSize().toPlainString() + " 亿元，近三年增速 "
                + String.join("/", profile.growthRates().stream().map(BigDecimal::toPlainString).toList())
                + "%，政策导向 " + profile.policyOrientation() + "，CR5 " + profile.concentrationCr5().toPlainString()
                + "%，周期阶段 " + profile.cycleStage() + "；主要风险："
                + String.join("、", profile.mainRisks());
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.INDUSTRY_RESEARCH,
                EvidenceSourceType.INDUSTRY_RESEARCH, SOURCE_SYSTEM, "INDUSTRY_PROFILE:" + profile.industryCode(),
                subjectId(request), profile.dataPeriod(), summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("resolved", true);
        return ToolResult.success(SOURCE_SYSTEM, profile, List.of(String.valueOf(evidenceId)), metadata);
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        String value = request.stringParam("enterpriseId");
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }
}
