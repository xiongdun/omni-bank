package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.enums.EvidenceSourceType;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.support.DdMockEvidence;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 行政处罚查询工具（Mock）：返回税务、环保、市场监管处罚记录及整改状态，并登记司法记录证据。
 */
@Component
@RequiredArgsConstructor
public class MockAdministrativePenaltiesTool implements AgentTool<AdministrativePenaltiesSnapshot> {

    public static final String TOOL_CODE = "judicial.getAdministrativePenalties";

    private static final String SOURCE_SYSTEM = "MOCK_PENALTY_PROVIDER";

    private static final String[] AUTHORITIES = {"国家税务总局南昌市税务局", "南昌市生态环境局", "南昌市市场监督管理局"};

    private static final String[] CATEGORIES = {"税务", "环保", "市场监管"};

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "行政处罚查询（Mock）",
                "按主体返回税务、环保与市场监管行政处罚记录，包含决定书号、金额与整改状态", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<AdministrativePenaltiesSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        String enterpriseName = request.stringParam("enterpriseName");
        int seed = MockToolSupport.seed(creditCode);
        String matchedSubject = MockToolSupport.matchedSubject(enterpriseName, creditCode);
        String matchMode = MockToolSupport.matchMode(creditCode);
        int categoryIndex = seed % CATEGORIES.length;

        List<AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord> records = new ArrayList<>();
        records.add(new AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord(
                decisionNo(2025, seed, 0), AUTHORITIES[categoryIndex], CATEGORIES[categoryIndex],
                amount(10_000 + seed % 10 * 5_000), LocalDate.of(2025, seed % 12 + 1, 5),
                seed % 3 != 0, matchedSubject, matchMode));
        if (seed % 4 == 0) {
            int secondIndex = (categoryIndex + 1) % CATEGORIES.length;
            records.add(new AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord(
                    decisionNo(2024, seed, 1), AUTHORITIES[secondIndex], CATEGORIES[secondIndex],
                    amount(20_000 + seed % 8 * 4_000), LocalDate.of(2024, seed % 12 + 1, 18),
                    true, matchedSubject, matchMode));
        }

        String summary = "【MOCK】行政处罚 " + records.size() + " 条，未整改 "
                + records.stream().filter(record -> !record.rectified()).count()
                + " 条，主体匹配方式=" + matchMode + "（模拟数据，仅用于链路验证）";
        String reference = "MOCK-ADMINISTRATIVE-PENALTIES:" + MockToolSupport.normalizedCode(creditCode);
        String dataPeriod = LocalDate.now().toString();
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                MockToolSupport.enterpriseId(request.stringParam("enterpriseId")),
                DdMockEvidence.item(EvidenceType.JUDICIAL_RECORD, EvidenceSourceType.EXTERNAL_DATA,
                        SOURCE_SYSTEM, reference, MockToolSupport.normalizedCode(creditCode), dataPeriod,
                        summary, new BigDecimal("0.85")));
        return ToolResult.success(SOURCE_SYSTEM,
                new AdministrativePenaltiesSnapshot(evidenceId, SOURCE_SYSTEM, reference, dataPeriod, summary,
                        !MockToolSupport.fuzzyMatch(creditCode), matchedSubject, List.copyOf(records)),
                List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "evidenceType", EvidenceType.JUDICIAL_RECORD.name()));
    }

    private String decisionNo(int year, int seed, int sequence) {
        return "洪罚〔" + year + "〕" + (100 + seed % 800 + sequence) + "号";
    }

    private BigDecimal amount(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
