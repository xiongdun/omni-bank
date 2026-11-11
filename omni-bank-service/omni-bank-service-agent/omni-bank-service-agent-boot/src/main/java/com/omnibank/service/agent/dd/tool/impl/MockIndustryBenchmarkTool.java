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
 * 行业基准查询工具（Mock）：返回毛利率、资产负债率、应收账款周转天数与营收增速中位数。
 * 基准数据来自 Mock 行业基准库，附带统计期间与样本口径，未匹配行业时返回 UNKNOWN 且不登记证据。
 */
@Component
@RequiredArgsConstructor
public class MockIndustryBenchmarkTool implements AgentTool<IndustryBenchmarkSnapshot> {

    public static final String TOOL_CODE = "industry.getIndustryBenchmark";

    private static final String SOURCE_SYSTEM = "MOCK_INDUSTRY_BENCHMARK_PROVIDER";

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.65");

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "行业基准查询（Mock）",
                "返回行业毛利率、资产负债率、应收账款周转天数与营收增速中位数，数据为 Mock 行业基准",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<IndustryBenchmarkSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        IndustryBenchmarkSnapshot benchmark = MockIndustryCatalog.resolveBenchmark(
                request.stringParam("industryName"), request.stringParam("businessScope"),
                request.stringParam("enterpriseName"));
        if (MockIndustryCatalog.UNKNOWN_CODE.equals(benchmark.industryCode())) {
            return ToolResult.success(SOURCE_SYSTEM, benchmark, List.of(),
                    Map.of("mock", true, "resolved", false));
        }

        String summary = "Mock 行业基准：" + benchmark.industryName() + "（" + benchmark.industryCode()
                + "），毛利率中位数 " + benchmark.grossMarginMedian().toPlainString()
                + "%，资产负债率中位数 " + benchmark.assetLiabilityRatioMedian().toPlainString()
                + "%，应收账款周转天数中位数 " + benchmark.receivableTurnoverDaysMedian()
                + " 天，营收增速中位数 " + benchmark.revenueGrowthMedian().toPlainString()
                + "%；口径：" + benchmark.sampleNote();
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.INDUSTRY_RESEARCH,
                EvidenceSourceType.INDUSTRY_RESEARCH, SOURCE_SYSTEM, "INDUSTRY_BENCHMARK:"
                        + benchmark.industryCode(), subjectId(request), benchmark.dataPeriod(), summary, CONFIDENCE);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("resolved", true);
        return ToolResult.success(SOURCE_SYSTEM, benchmark, List.of(String.valueOf(evidenceId)), metadata);
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
