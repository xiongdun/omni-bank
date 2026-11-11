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
 * 司法涉诉记录查询工具（Mock）：按统一社会信用代码派生确定性案件数据，并登记司法记录证据。
 * 真实司法数据接口未确认前不直连外部系统，数据仅用于链路验证。
 */
@Component
@RequiredArgsConstructor
public class MockJudicialRecordsTool implements AgentTool<JudicialRecordsSnapshot> {

    public static final String TOOL_CODE = "judicial.getJudicialRecords";

    private static final String SOURCE_SYSTEM = "MOCK_JUDICIAL_PROVIDER";

    private static final String COURT = "江西省南昌市中级人民法院";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "司法涉诉记录查询（Mock）",
                "按主体返回涉诉案件列表，包含案号、案由、当事人角色、涉案金额与案件阶段", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<JudicialRecordsSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        String enterpriseName = request.stringParam("enterpriseName");
        int seed = MockToolSupport.seed(creditCode);
        String matchedSubject = MockToolSupport.matchedSubject(enterpriseName, creditCode);
        String matchMode = MockToolSupport.matchMode(creditCode);

        List<JudicialRecordsSnapshot.JudicialCase> cases = new ArrayList<>();
        LocalDate plaintiffFiling = LocalDate.of(2024, seed % 12 + 1, 10);
        cases.add(new JudicialRecordsSnapshot.JudicialCase(caseNo(2024, seed, 0), "买卖合同纠纷", "原告",
                amount(800_000 + seed % 5 * 100_000), plaintiffFiling, plaintiffFiling.plusMonths(4),
                "已结案", COURT, matchedSubject, matchMode));
        LocalDate defendantFiling = LocalDate.of(2025, seed % 12 + 1, 15);
        cases.add(new JudicialRecordsSnapshot.JudicialCase(caseNo(2025, seed, 1), "金融借款合同纠纷", "被告",
                amount(200_000 + seed % 9 * 60_000), defendantFiling, null,
                "审理中", COURT, matchedSubject, matchMode));
        if (seed % 3 == 0) {
            LocalDate thirdFiling = LocalDate.of(2024, seed % 12 + 1, 20);
            cases.add(new JudicialRecordsSnapshot.JudicialCase(caseNo(2024, seed, 2), "建设工程施工合同纠纷",
                    "第三人", amount(50_000 + seed % 4 * 30_000), thirdFiling, thirdFiling.plusMonths(6),
                    "已结案", COURT, matchedSubject, matchMode));
        }

        String summary = "【MOCK】涉诉记录 " + cases.size() + " 件，主体匹配方式=" + matchMode
                + "，命中主体=" + matchedSubject + "（模拟数据，仅用于链路验证）";
        String reference = "MOCK-JUDICIAL-RECORDS:" + MockToolSupport.normalizedCode(creditCode);
        String dataPeriod = LocalDate.now().toString();
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                MockToolSupport.enterpriseId(request.stringParam("enterpriseId")),
                DdMockEvidence.item(EvidenceType.JUDICIAL_RECORD, EvidenceSourceType.EXTERNAL_DATA,
                        SOURCE_SYSTEM, reference, MockToolSupport.normalizedCode(creditCode), dataPeriod,
                        summary, new BigDecimal("0.85")));
        return ToolResult.success(SOURCE_SYSTEM,
                new JudicialRecordsSnapshot(evidenceId, SOURCE_SYSTEM, reference, dataPeriod, summary,
                        !MockToolSupport.fuzzyMatch(creditCode), matchedSubject, List.copyOf(cases)),
                List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "evidenceType", EvidenceType.JUDICIAL_RECORD.name()));
    }

    private String caseNo(int year, int seed, int sequence) {
        return "(" + year + ")赣" + String.format("%02d", seed % 20 + 1) + "民初"
                + (1000 + seed % 8000 + sequence) + "号";
    }

    private BigDecimal amount(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
