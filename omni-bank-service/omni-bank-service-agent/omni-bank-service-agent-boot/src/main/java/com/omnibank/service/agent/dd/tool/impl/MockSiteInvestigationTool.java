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
 * 现场调查记录查询工具（Mock）：返回客户经理现场观察事实并登记现场调查证据。
 * 记录只描述现场可见事实（FACT），不代表已核实的经营结论。
 */
@Component
@RequiredArgsConstructor
public class MockSiteInvestigationTool implements AgentTool<SiteInvestigationSnapshot> {

    public static final String TOOL_CODE = "siteVisit.getInvestigationRecords";

    private static final String SOURCE_SYSTEM = "MOCK_SITE_VISIT_PROVIDER";

    /** 现场观察事实的数据性质标识 */
    private static final String DATA_NATURE = "FACT";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "现场调查记录查询（Mock）",
                "按主体返回现场调查记录，包含调查日期、地址、经营状态、在岗人数、设备开机率与照片数量",
                "QUERY", true, 3000);
    }

    @Override
    public ToolResult<SiteInvestigationSnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        String creditCode = request.stringParam("creditCode");
        String enterpriseName = request.stringParam("enterpriseName");
        if (isBlank(creditCode) && isBlank(enterpriseName)) {
            return ToolResult.notFound(SOURCE_SYSTEM, "未找到现场调查记录");
        }
        int seed = MockToolSupport.seed(creditCode);
        String investigationDate = LocalDate.of(2026, 1, 1).plusDays(seed % 280).toString();
        String siteAddress = siteAddress(request.stringParam("registeredAddress"), seed);
        String productionStatus = seed % 5 == 0 ? "部分停产" : "正常生产";
        int onSiteStaffCount = 15 + seed % 30;
        BigDecimal equipmentOperatingRate = BigDecimal.valueOf(35 + seed % 55).setScale(2, RoundingMode.HALF_UP);
        String inventoryObservation = "原材料库存约 " + (seed % 5 + 1) + " 个月用量，成品库存在库 "
                + (seed % 3 + 1) + " 批（现场观察，Mock）";
        int photoCount = seed % 7;
        String investigator = "客户经理（工号 M" + (1000 + seed % 9000) + "）";

        String summary = "【MOCK】现场调查日期=" + investigationDate + "；地址=" + siteAddress
                + "；经营状态=" + productionStatus + "；在岗人数=" + onSiteStaffCount
                + "；设备开机率=" + equipmentOperatingRate.toPlainString() + "%；照片=" + photoCount
                + " 张（现场观察事实，模拟数据，仅用于链路验证）";
        String reference = "MOCK-SITE-INVESTIGATION:" + MockToolSupport.normalizedCode(creditCode);
        Long evidenceId = evidenceService.recordEvidence(request.taskId(),
                MockToolSupport.enterpriseId(request.stringParam("enterpriseId")),
                DdMockEvidence.item(EvidenceType.SITE_INVESTIGATION, EvidenceSourceType.MANUAL_INVESTIGATION,
                        SOURCE_SYSTEM, reference, MockToolSupport.normalizedCode(creditCode), investigationDate,
                        summary, new BigDecimal("0.80")));
        return ToolResult.success(SOURCE_SYSTEM,
                new SiteInvestigationSnapshot(evidenceId, SOURCE_SYSTEM, reference, investigationDate, summary,
                        investigationDate, siteAddress, productionStatus, onSiteStaffCount,
                        equipmentOperatingRate, inventoryObservation, photoCount, investigator, DATA_NATURE),
                List.of(String.valueOf(evidenceId)),
                Map.of("mock", true, "evidenceType", EvidenceType.SITE_INVESTIGATION.name()));
    }

    /** 有工商注册地址时用于比对，无地址时返回可识别的 Mock 现场地址 */
    private String siteAddress(String registeredAddress, int seed) {
        if (isBlank(registeredAddress)) {
            return "江西省南昌市高新技术产业开发区创新大道 88 号（Mock 现场地址）";
        }
        String registered = registeredAddress.trim();
        return seed % 4 == 0 ? registered + "（二期厂房）" : registered;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
