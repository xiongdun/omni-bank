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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 管理层访谈记录查询工具（Mock）：返访谈陈述并登记现场调查证据。
 * 访谈内容属于陈述（STATEMENT），不得作为已确认事实使用。
 */
@Component
@RequiredArgsConstructor
public class MockInterviewRecordsTool implements AgentTool<InterviewSnapshot> {

    public static final String TOOL_CODE = "siteVisit.getInterviewRecords";

    private static final String SOURCE_SYSTEM = "MOCK_INTERVIEW_PROVIDER";

    /** 访谈陈述的数据性质标识 */
    private static final String DATA_NATURE = "STATEMENT";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "管理层访谈记录查询（Mock）",
                "按主体返回管理层访谈记录，包含产能利用率、在手订单、员工人数与上下游陈述",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<InterviewSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        String enterpriseName = request.stringParam("enterpriseName");
        if (isBlank(creditCode) && isBlank(enterpriseName)) {
            return ToolResult.notFound(SOURCE_SYSTEM, "未找到管理层访谈记录");
        }
        int seed = MockToolSupport.seed(creditCode);
        String interviewDate = LocalDate.of(2026, 1, 1).plusDays(seed % 280).plusDays(1).toString();
        BigDecimal capacityUtilizationRate = BigDecimal.valueOf(40 + seed % 55).setScale(2, RoundingMode.HALF_UP);
        String orderBacklog = "在手订单约 " + (seed % 12 + 1) + " 个月产能（访谈陈述，未经现场核实）";
        int employeeCount = 12 + seed % 32;
        String mainCustomersStatement = "前五大客户集中度约 " + (40 + seed % 30) + "%（访谈陈述）";
        String mainSuppliersStatement = "主要原材料供应商 " + (seed % 5 + 2) + " 家，平均账期 "
                + (30 + seed % 3 * 15) + " 天（访谈陈述）";
        String interviewee = "企业财务负责人（访谈对象 Mock）";

        String summary = "【MOCK】访谈日期=" + interviewDate + "；声称产能利用率="
                + capacityUtilizationRate.toPlainString() + "%；声称员工人数=" + employeeCount
                + "；在手订单=" + orderBacklog + "（访谈陈述，模拟数据，仅用于链路验证）";
        String reference = "MOCK-INTERVIEW-RECORDS:" + MockToolSupport.normalizedCode(creditCode);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                MockToolSupport.enterpriseId(request.stringParam("enterpriseId")),
                DdMockEvidence.item(EvidenceType.SITE_INVESTIGATION, EvidenceSourceType.MANUAL_INVESTIGATION,
                        SOURCE_SYSTEM, reference, MockToolSupport.normalizedCode(creditCode), interviewDate,
                        summary, new BigDecimal("0.70")));
        return ToolResult.success(SOURCE_SYSTEM,
                new InterviewSnapshot(evidenceId, SOURCE_SYSTEM, reference, interviewDate, summary,
                        interviewDate, capacityUtilizationRate, orderBacklog, employeeCount,
                        mainCustomersStatement, mainSuppliersStatement, interviewee, DATA_NATURE),
                List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "evidenceType", EvidenceType.SITE_INVESTIGATION.name()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
