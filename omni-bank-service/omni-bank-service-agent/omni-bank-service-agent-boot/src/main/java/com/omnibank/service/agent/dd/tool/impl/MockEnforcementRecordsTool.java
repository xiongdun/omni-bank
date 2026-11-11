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
 * 执行类记录查询工具（Mock）：返回被执行人、失信被执行人与限制高消费记录，并登记司法记录证据。
 * 数据由统一社会信用代码派生，同一主体重复执行结果保持一致。
 */
@Component
@RequiredArgsConstructor
public class MockEnforcementRecordsTool implements AgentTool<EnforcementRecordsSnapshot> {

    public static final String TOOL_CODE = "judicial.getEnforcementRecords";

    private static final String SOURCE_SYSTEM = "MOCK_ENFORCEMENT_PROVIDER";

    private static final String COURT = "江西省南昌市中级人民法院";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "执行类记录查询（Mock）",
                "按主体返回被执行人、失信被执行人与限制高消费记录", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<EnforcementRecordsSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        String enterpriseName = request.stringParam("enterpriseName");
        int seed = MockToolSupport.seed(creditCode);
        String matchedSubject = MockToolSupport.matchedSubject(enterpriseName, creditCode);
        String matchMode = MockToolSupport.matchMode(creditCode);
        String caseNo = "(2025)赣" + String.format("%02d", seed % 20 + 1) + "执" + (100 + seed % 900) + "号";
        LocalDate filingDate = LocalDate.of(2025, seed % 12 + 1, 8);
        BigDecimal enforcementAmount = amount(150_000 + seed % 6 * 50_000);

        List<EnforcementRecordsSnapshot.EnforcementRecord> records = new ArrayList<>();
        records.add(new EnforcementRecordsSnapshot.EnforcementRecord("被执行人", caseNo, matchedSubject,
                enforcementAmount, filingDate, "执行中", COURT, matchMode));
        if (seed % 4 == 0) {
            records.add(new EnforcementRecordsSnapshot.EnforcementRecord("失信被执行人", caseNo + "-1",
                    matchedSubject, enforcementAmount, filingDate.plusMonths(1), "执行中", COURT, matchMode));
        }
        if (seed % 5 == 0) {
            records.add(new EnforcementRecordsSnapshot.EnforcementRecord("限制高消费", caseNo + "-2",
                    matchedSubject, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    filingDate.plusMonths(2), "执行中", COURT, matchMode));
        }

        String summary = "【MOCK】执行类记录 " + records.size() + " 条，被执行人标的合计 "
                + enforcementAmount.toPlainString() + " 元，主体匹配方式=" + matchMode
                + "（模拟数据，仅用于链路验证）";
        String reference = "MOCK-ENFORCEMENT-RECORDS:" + MockToolSupport.normalizedCode(creditCode);
        String dataPeriod = LocalDate.now().toString();
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                MockToolSupport.enterpriseId(request.stringParam("enterpriseId")),
                DdMockEvidence.item(EvidenceType.JUDICIAL_RECORD, EvidenceSourceType.EXTERNAL_DATA,
                        SOURCE_SYSTEM, reference, MockToolSupport.normalizedCode(creditCode), dataPeriod,
                        summary, new BigDecimal("0.85")));
        return ToolResult.success(SOURCE_SYSTEM,
                new EnforcementRecordsSnapshot(evidenceId, SOURCE_SYSTEM, reference, dataPeriod, summary,
                        !MockToolSupport.fuzzyMatch(creditCode), matchedSubject, List.copyOf(records)),
                List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "evidenceType", EvidenceType.JUDICIAL_RECORD.name()));
    }

    private BigDecimal amount(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
