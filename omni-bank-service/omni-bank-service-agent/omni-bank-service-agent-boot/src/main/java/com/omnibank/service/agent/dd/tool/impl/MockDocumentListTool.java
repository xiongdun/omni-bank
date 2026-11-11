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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 材料清单查询工具（Mock）：系统尚未接入影像平台，按统一社会信用代码派生确定性的材料清单。
 * 清单只用于链路验证，不代表客户真实上传行为，Mock 标记随定义与结果返回。
 */
@Component
@RequiredArgsConstructor
public class MockDocumentListTool implements AgentTool<DocumentMaterialsSnapshot> {

    public static final String TOOL_CODE = "document.listMaterials";

    private static final String SOURCE_SYSTEM = "MOCK_DOCUMENT_PROVIDER";

    private static final String DATA_PERIOD = "2025年度";

    private static final LocalDate BASE_UPLOAD_DATE = LocalDate.of(2025, 1, 6);

    private static final LocalDate LONG_VALID = LocalDate.of(2035, 12, 31);

    private static final LocalDate EXPIRED_VALID = LocalDate.of(2024, 12, 31);

    /** 必备材料模板：文件名、页数、可读文本摘要、有效期 */
    private static final List<MaterialTemplate> REQUIRED_TEMPLATES = List.of(
            new MaterialTemplate("营业执照.jpg", 1,
                    "营业执照 统一社会信用代码 名称 住所 法定代表人 经营范围", LONG_VALID),
            new MaterialTemplate("公司章程.pdf", 12,
                    "公司章程 股东出资 股权结构 议事规则 增资减资", LONG_VALID),
            new MaterialTemplate("财务报表_2024年度.pdf", 30,
                    "资产负债表 利润表 现金流量表 营业收入 净利润 毛利率", LONG_VALID),
            new MaterialTemplate("银行流水_近6个月.pdf", 18,
                    "银行流水 交易明细 收支 对账单 账户余额", LONG_VALID),
            new MaterialTemplate("纳税证明_2024年度.pdf", 3,
                    "纳税证明 完税 增值税 企业所得税 纳税信用等级", LONG_VALID),
            new MaterialTemplate("征信授权书.pdf", 2,
                    "征信授权书 授权查询 人民银行征信 企业信用报告", LONG_VALID));

    /** 担保方式为抵押/质押/保证时额外必备的材料 */
    private static final List<MaterialTemplate> GUARANTEE_TEMPLATES = List.of(
            new MaterialTemplate("担保合同_抵押.pdf", 10,
                    "担保合同 抵押 保证 担保范围 担保期间", LONG_VALID),
            new MaterialTemplate("不动产权证_厂房.pdf", 4,
                    "不动产权证 权属 权利人 抵押 他项权证", LONG_VALID));

    private static final String UNRECOGNIZED_FILE = "扫描件_补录.pdf";

    private static final String UNRECOGNIZED_EXCERPT = "扫描件 图像倾斜 字迹模糊 无有效关键字";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "材料清单查询（Mock）",
                "按统一社会信用代码派生确定性的 Mock 材料清单，系统尚未接入影像平台与真实上传", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<DocumentMaterialsSnapshot> execute(ToolRequest request) {
        String creditCode = request.stringParam("creditCode");
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        if (creditCode == null || creditCode.isBlank()) {
            return ToolResult.failure(TOOL_CODE, null, "creditCode 不能为空");
        }
        boolean hasGuarantee = Boolean.parseBoolean(request.stringParam("hasGuarantee"));
        List<DocumentMaterialSnapshot> materials = buildMaterials(creditCode, hasGuarantee);

        String summary = "Mock 材料清单（非真实影像平台上传）：统一社会信用代码【" + creditCode + "】，共 "
                + materials.size() + " 份材料，材料类型与时效由材料识别 Agent 判定";
        ExternalDataItem item = DdMockEvidence.item(EvidenceType.CUSTOMER_DOCUMENT,
                EvidenceSourceType.CUSTOMER_UPLOAD, SOURCE_SYSTEM, "MATERIAL_LIST:" + creditCode,
                subjectId(request), DATA_PERIOD, summary, new BigDecimal("0.90"));
        Long evidenceId = evidenceService.recordEvidence(request.taskId(), enterpriseId(request), item);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mock", true);
        metadata.put("materialCount", materials.size());
        return ToolResult.success(SOURCE_SYSTEM,
                new DocumentMaterialsSnapshot(materials, evidenceId), List.of(String.valueOf(evidenceId)), metadata);
    }

    /** 清单内容只由信用代码派生：缺失、重复、过期、无法归类均在派生时确定 */
    private List<DocumentMaterialSnapshot> buildMaterials(String creditCode, boolean hasGuarantee) {
        int seed = Math.abs(creditCode.hashCode());
        List<MaterialTemplate> templates = new ArrayList<>(REQUIRED_TEMPLATES);
        templates.remove(1 + seed % (REQUIRED_TEMPLATES.size() - 1));

        List<MaterialTemplate> selected = new ArrayList<>(templates);
        if (hasGuarantee) {
            selected.addAll(GUARANTEE_TEMPLATES);
        }
        if (seed % 2 == 0) {
            selected.add(REQUIRED_TEMPLATES.getFirst());
        } else {
            selected.add(new MaterialTemplate(UNRECOGNIZED_FILE, 5, UNRECOGNIZED_EXCERPT, LONG_VALID));
        }

        List<DocumentMaterialSnapshot> materials = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            MaterialTemplate template = selected.get(index);
            boolean expiredAuthorization = template.fileName().contains("征信授权") && seed % 3 == 0;
            boolean expiredLicense = template.fileName().contains("营业执照") && seed % 5 == 0;
            LocalDate validUntil = expiredAuthorization || expiredLicense ? EXPIRED_VALID : template.validUntil();
            materials.add(new DocumentMaterialSnapshot(template.fileName(), extensionOf(template.fileName()),
                    template.pageCount(), BASE_UPLOAD_DATE.plusDays(index).atTime(9 + seed % 8, seed % 60),
                    fileHash(creditCode, template.fileName() + "|" + template.excerpt()),
                    template.excerpt(), validUntil));
        }
        return materials;
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1);
    }

    private String fileHash(String creditCode, String contentKey) {
        return DdMockEvidence.sha256(creditCode + "|" + contentKey).substring(0, 32);
    }

    private String subjectId(ToolRequest request) {
        String enterpriseId = request.stringParam("enterpriseId");
        return enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
    }

    private Long enterpriseId(ToolRequest request) {
        String value = request.stringParam("enterpriseId");
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private record MaterialTemplate(String fileName, int pageCount, String excerpt, LocalDate validUntil) {
    }
}
