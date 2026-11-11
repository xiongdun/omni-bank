package com.omnibank.service.agent.dd.ai.agent;

import com.omnibank.service.agent.dd.ai.AgentContext;
import com.omnibank.service.agent.dd.ai.AgentDataGap;
import com.omnibank.service.agent.dd.ai.AgentDefinition;
import com.omnibank.service.agent.dd.ai.AgentExecutionStatus;
import com.omnibank.service.agent.dd.ai.AgentFinding;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentSupplementRequest;
import com.omnibank.service.agent.dd.ai.CreditAgent;
import com.omnibank.service.agent.dd.ai.EvidenceRef;
import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.impl.DocumentClassification;
import com.omnibank.service.agent.dd.tool.impl.DocumentMaterialSnapshot;
import com.omnibank.service.agent.dd.tool.impl.DocumentMaterialsSnapshot;
import com.omnibank.service.agent.dd.tool.impl.MaterialDocTypes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 06 材料识别：系统尚未接入影像平台与真实 OCR，当前通过 Mock 材料源模拟已上传材料清单。
 * 规则引擎完成材料分类、必备材料完整性、重复、过期与无法归类检查，所有结论显式标记 Mock，禁止伪装真实 OCR。
 */
@Component
@RequiredArgsConstructor
public class DocumentRecognitionAgent implements CreditAgent<Void, DocumentRecognitionData> {

    public static final String AGENT_CODE = "DOCUMENT_RECOGNITION";

    private static final String TOOL_LIST_MATERIALS = "document.listMaterials";

    private static final String TOOL_CLASSIFY_DOCUMENT = "document.classifyDocument";

    private static final String TOOL_CREATE_EVIDENCE = "document.createEvidence";

    private static final String SOURCE_SYSTEM_MATERIALS = "MOCK_DOCUMENT_PROVIDER";

    private static final String SOURCE_SYSTEM_OCR = "MOCK_OCR_PROVIDER";

    /** 基础必备材料 */
    private static final List<RequiredDocument> REQUIRED_DOCUMENTS = List.of(
            new RequiredDocument(MaterialDocTypes.BUSINESS_LICENSE, "营业执照"),
            new RequiredDocument(MaterialDocTypes.ARTICLES_OF_ASSOCIATION, "公司章程"),
            new RequiredDocument(MaterialDocTypes.FINANCIAL_STATEMENT, "近一年财务报表"),
            new RequiredDocument(MaterialDocTypes.BANK_STATEMENT, "近 6 个月银行流水"),
            new RequiredDocument(MaterialDocTypes.TAX_CERTIFICATE, "纳税证明"),
            new RequiredDocument(MaterialDocTypes.CREDIT_AUTHORIZATION, "征信授权书"));

    /** 抵押/质押/保证担保场景追加的必备材料 */
    private static final List<RequiredDocument> GUARANTEE_DOCUMENTS = List.of(
            new RequiredDocument(MaterialDocTypes.GUARANTEE_CONTRACT, "担保合同"),
            new RequiredDocument(MaterialDocTypes.PROPERTY_CERTIFICATE, "抵押物权证或不动产权证"));

    private static final AgentDefinition DEFINITION = buildDefinition();

    private final ToolRegistry toolRegistry;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    public String getAgentName() {
        return "材料识别 Agent";
    }

    @Override
    public AgentDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public AgentResult<DocumentRecognitionData> execute(AgentContext context, Void input) {
        List<AgentFinding> findings = new ArrayList<>();
        List<AgentDataGap> gaps = new ArrayList<>();
        List<AgentSupplementRequest> supplementRequests = new ArrayList<>();
        List<EvidenceRef> evidenceRefs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DocumentRecognitionData data = new DocumentRecognitionData();
        warnings.add("当前材料清单与识别结果由 Mock 材料源生成（MOCK_DOCUMENT_PROVIDER / MOCK_OCR_PROVIDER），"
                + "不代表真实上传文件或真实 OCR 结果");

        String creditCode = context.getEnterpriseProfile() == null
                ? null : context.getEnterpriseProfile().getCreditCode();
        if (isBlank(creditCode)) {
            gaps.add(dataGap("CREDIT_CODE", "统一社会信用代码", "上下文缺少统一社会信用代码，无法派生材料清单",
                    true, "NOT_PROVIDED", "无法识别客户材料", "补充企业主体信息与统一社会信用代码"));
            supplementRequests.add(supplement("CREDIT_CODE", "统一社会信用代码", "材料识别缺少主体标识",
                    "补充企业主体信息或上传营业执照"));
            data.setMaterialCount(0);
            data.setMaterials(List.of());
            data.setEvidenceCompleteness(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            data.setRequiredMaterialsComplete(false);
            data.setEvidenceCount(0);
            data.setRecognizedDocTypes(List.of());
            data.setMissingDocuments(List.of());
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), data.getEvidenceCompleteness(), "材料识别未完成：缺少统一社会信用代码");
        }

        boolean hasGuarantee = hasGuarantee(context.getCreditApplication());
        List<RequiredDocument> required = new ArrayList<>(REQUIRED_DOCUMENTS);
        if (hasGuarantee) {
            required.addAll(GUARANTEE_DOCUMENTS);
        }

        DocumentMaterialsSnapshot materials = listMaterials(context, creditCode, hasGuarantee);
        if (materials == null) {
            gaps.add(dataGap("MATERIAL_LIST", "客户材料清单", "材料清单工具未返回结果", true, "UNAVAILABLE",
                    "无法识别客户材料", "重新获取材料清单"));
            supplementRequests.add(supplement("MATERIAL_LIST", "客户材料清单", "材料识别缺少清单",
                    "重新上传或重新采集客户材料"));
            data.setMaterialCount(0);
            data.setMaterials(List.of());
            data.setEvidenceCompleteness(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            data.setRequiredMaterialsComplete(false);
            data.setEvidenceCount(0);
            data.setRecognizedDocTypes(List.of());
            data.setMissingDocuments(List.of());
            return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                    new BigDecimal("0.30"), data.getEvidenceCompleteness(), "材料识别未完成：材料清单不可用");
        }
        addEvidenceRef(evidenceRefs, materials.evidenceId(), SOURCE_SYSTEM_MATERIALS, "MATERIAL_LIST:" + creditCode,
                "2025年度", "Mock 材料清单：" + materials.materials().size() + " 份材料");

        List<DocumentRecognitionData.MaterialSummary> summaries = new ArrayList<>();
        List<String> recognizedDocTypes = new ArrayList<>();
        List<String> missingDocuments = new ArrayList<>();
        Map<String, List<RecognizedMaterial>> sameContentGroups = new LinkedHashMap<>();
        Set<Long> createdEvidenceIds = new LinkedHashSet<>();
        int expiredCount = 0;
        int unrecognizedCount = 0;

        for (DocumentMaterialSnapshot material : materials.materials()) {
            DocumentClassification classification = classify(context, material);
            boolean expired = material.validUntil() != null && material.validUntil().isBefore(LocalDate.now());
            summaries.add(materialSummary(material, classification, expired));

            if (MaterialDocTypes.UNRECOGNIZED.equals(classification.docType())) {
                unrecognizedCount++;
                findings.add(finding("DOC_UNRECOGNIZED", "DOCUMENT", "LOW", "材料无法归类：" + material.fileName(),
                        "材料【" + material.fileName() + "】未命中任何分类关键字，无法确定材料类型；"
                                + "Mock 文本摘要：" + material.textExcerpt(),
                        "NEEDS_VERIFICATION", new BigDecimal("0.60"), evidenceRefs,
                        "人工确认材料类型与业务用途后重新上传"));
                continue;
            }
            if (!recognizedDocTypes.contains(classification.docType())) {
                recognizedDocTypes.add(classification.docType());
            }
            sameContentGroups.computeIfAbsent(classification.docType() + "|" + material.fileHash(),
                    key -> new ArrayList<>())
                    .add(new RecognizedMaterial(material, classification));

            if (expired) {
                expiredCount++;
                findings.add(finding("DOC_EXPIRED", "DOCUMENT", "HIGH", "材料已过期：" + classification.docTypeName(),
                        "材料【" + material.fileName() + "】有效期至 " + material.validUntil()
                                + "，已超过有效期，不能作为当前尽调依据",
                        "CONFIRMED", new BigDecimal("0.90"), evidenceRefs,
                        "要求客户提供最新有效版本材料"));
            }

            Long evidenceId = createEvidence(context, material, classification);
            if (evidenceId != null && createdEvidenceIds.add(evidenceId)) {
                addEvidenceRef(evidenceRefs, evidenceId, SOURCE_SYSTEM_OCR, material.fileHash(),
                        dataPeriod(material), "材料【" + classification.docTypeName() + "】" + material.fileName());
            }
        }

        int duplicateGroups = 0;
        for (List<RecognizedMaterial> group : sameContentGroups.values()) {
            if (group.size() > 1) {
                duplicateGroups++;
                RecognizedMaterial first = group.getFirst();
                findings.add(finding("DOC_DUPLICATE", "DOCUMENT", "LOW",
                        "重复材料：" + first.classification().docTypeName(),
                        "材料【" + first.classification().docTypeName() + "】存在 " + group.size()
                                + " 份相同内容副本（文件哈希 " + first.material().fileHash() + "）",
                        "CONFIRMED", new BigDecimal("0.95"), evidenceRefs,
                        "确认是否重复上传，保留有效版本并清理冗余材料"));
            }
        }

        long recognizedRequired = required.stream()
                .filter(document -> recognizedDocTypes.contains(document.docType()))
                .count();
        for (RequiredDocument document : required) {
            if (recognizedDocTypes.contains(document.docType())) {
                continue;
            }
            missingDocuments.add(document.name());
            findings.add(finding("DOC_MISSING_REQUIRED", "DOCUMENT", "MEDIUM", "缺少必备材料：" + document.name(),
                    "Mock 材料清单中未识别到必备材料【" + document.name() + "】，可能影响尽调完整性与结论可靠性",
                    "NEEDS_VERIFICATION", new BigDecimal("0.80"), evidenceRefs,
                    "请客户经理向客户收集并上传" + document.name()));
            gaps.add(dataGap("DOCUMENT_" + document.docType(), document.name(),
                    "材料清单中未识别到必备材料：" + document.name(), true, "NOT_PROVIDED",
                    "相关分析缺少材料依据", "补充" + document.name()));
            supplementRequests.add(supplement("DOCUMENT_" + document.docType(), document.name(),
                    "必备材料缺失：" + document.name(), "向客户收集" + document.name() + "并上传影像件"));
        }

        BigDecimal completeness = BigDecimal.valueOf(recognizedRequired)
                .divide(BigDecimal.valueOf(required.size()), 2, RoundingMode.HALF_UP);
        data.setMaterialCount(materials.materials().size());
        data.setMaterials(summaries);
        data.setRecognizedDocTypes(recognizedDocTypes);
        data.setMissingDocuments(missingDocuments);
        data.setEvidenceCompleteness(completeness);
        data.setRequiredMaterialsComplete(missingDocuments.isEmpty());
        data.setEvidenceCount(evidenceRefs.size());

        BigDecimal confidence = findings.isEmpty() ? new BigDecimal("0.85") : new BigDecimal("0.70");
        String summary = "材料识别完成（Mock 材料源）：共 " + materials.materials().size() + " 份材料，必备材料 "
                + recognizedRequired + "/" + required.size() + "，缺失 " + missingDocuments.size()
                + " 项、过期 " + expiredCount + " 份、重复 " + duplicateGroups + " 组、无法归类 "
                + unrecognizedCount + " 份；风险线索 " + findings.size() + " 条";
        return result(context, data, findings, gaps, supplementRequests, evidenceRefs, warnings,
                confidence, completeness, summary);
    }

    private DocumentMaterialsSnapshot listMaterials(AgentContext context, String creditCode, boolean hasGuarantee) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("creditCode", creditCode);
        params.put("enterpriseId", context.getEnterpriseId() == null ? "" : String.valueOf(context.getEnterpriseId()));
        params.put("hasGuarantee", String.valueOf(hasGuarantee));
        ToolResult<DocumentMaterialsSnapshot> result = call(TOOL_LIST_MATERIALS,
                new ToolRequest(context.getTaskId(), params));
        return result.isSuccess() ? result.getData() : null;
    }

    private DocumentClassification classify(AgentContext context, DocumentMaterialSnapshot material) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fileName", material.fileName());
        params.put("textExcerpt", material.textExcerpt() == null ? "" : material.textExcerpt());
        ToolResult<DocumentClassification> result = call(TOOL_CLASSIFY_DOCUMENT,
                new ToolRequest(context.getTaskId(), params));
        if (!result.isSuccess() || result.getData() == null) {
            return new DocumentClassification(MaterialDocTypes.UNRECOGNIZED, "无法归类", BigDecimal.ZERO,
                    "分类工具未返回结果");
        }
        return result.getData();
    }

    private Long createEvidence(AgentContext context, DocumentMaterialSnapshot material,
                                DocumentClassification classification) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enterpriseId", context.getEnterpriseId() == null ? "" : String.valueOf(context.getEnterpriseId()));
        params.put("docType", classification.docType());
        params.put("docTypeName", classification.docTypeName());
        params.put("fileName", material.fileName());
        params.put("fileHash", material.fileHash());
        params.put("dataPeriod", dataPeriod(material));
        params.put("textExcerpt", material.textExcerpt() == null ? "" : material.textExcerpt());
        ToolResult<Long> result = call(TOOL_CREATE_EVIDENCE, new ToolRequest(context.getTaskId(), params));
        return result.isSuccess() ? result.getData() : null;
    }

    private DocumentRecognitionData.MaterialSummary materialSummary(DocumentMaterialSnapshot material,
                                                                    DocumentClassification classification,
                                                                    boolean expired) {
        DocumentRecognitionData.MaterialSummary summary = new DocumentRecognitionData.MaterialSummary();
        summary.setFileName(material.fileName());
        summary.setDocType(classification.docType());
        summary.setDocTypeName(classification.docTypeName());
        summary.setConfidence(classification.confidence());
        summary.setExpired(expired);
        return summary;
    }

    private boolean hasGuarantee(LoanApplicationVO application) {
        if (application == null || isBlank(application.getGuaranteeType())) {
            return false;
        }
        String type = application.getGuaranteeType().toUpperCase();
        return type.contains("MORTGAGE") || type.contains("PLEDGE") || type.contains("GUARANTEE");
    }

    private String dataPeriod(DocumentMaterialSnapshot material) {
        return material.uploadedAt() == null ? "" : material.uploadedAt().toLocalDate().toString().substring(0, 7);
    }

    private void addEvidenceRef(List<EvidenceRef> evidenceRefs, Long evidenceId, String sourceSystem,
                                String sourceReference, String dataPeriod, String excerpt) {
        if (evidenceId == null) {
            return;
        }
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(String.valueOf(evidenceId));
        ref.setEvidenceType(EvidenceType.CUSTOMER_DOCUMENT.name());
        ref.setSourceSystem(sourceSystem);
        ref.setSourceReference(sourceReference);
        ref.setDataPeriod(dataPeriod);
        ref.setExcerpt(excerpt);
        evidenceRefs.add(ref);
    }

    @SuppressWarnings("unchecked")
    private <T> ToolResult<T> call(String toolCode, ToolRequest request) {
        return (ToolResult<T>) toolRegistry.require(toolCode).execute(request);
    }

    private AgentResult<DocumentRecognitionData> result(AgentContext context, DocumentRecognitionData data,
                                                        List<AgentFinding> findings, List<AgentDataGap> gaps,
                                                        List<AgentSupplementRequest> supplementRequests,
                                                        List<EvidenceRef> evidenceRefs, List<String> warnings,
                                                        BigDecimal confidence, BigDecimal completeness,
                                                        String summary) {
        AgentResult<DocumentRecognitionData> result = new AgentResult<>();
        result.setAgentCode(AGENT_CODE);
        result.setExecutionId(context.getExecutionId());
        result.setStatus(AgentExecutionStatus.SUCCEEDED.name());
        result.setSummary(summary);
        result.setData(data);
        result.setFindings(findings);
        result.setDataGaps(gaps);
        result.setSupplementRequests(supplementRequests);
        result.setEvidenceRefs(evidenceRefs);
        result.setConfidence(confidence);
        result.setEvidenceCompleteness(completeness);
        result.setWarnings(warnings);
        result.setModelVersion("rule-engine");
        result.setPromptVersion("rule-1.0");
        return result;
    }

    private AgentFinding finding(String code, String category, String severity, String title, String description,
                                 String status, BigDecimal confidence, List<EvidenceRef> evidenceRefs,
                                 String verificationSuggestion) {
        AgentFinding finding = new AgentFinding();
        finding.setFindingCode(code);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setDescription(description);
        finding.setStatus(status);
        finding.setConfidence(confidence);
        finding.setEvidenceRefs(List.copyOf(evidenceRefs));
        finding.setRelatedFindingCodes(List.of());
        finding.setVerificationSuggestion(verificationSuggestion);
        return finding;
    }

    private AgentDataGap dataGap(String dataCode, String dataName, String description, boolean required,
                                 String status, String impact, String suggestedAction) {
        AgentDataGap gap = new AgentDataGap();
        gap.setDataCode(dataCode);
        gap.setDataName(dataName);
        gap.setDescription(description);
        gap.setRequired(required);
        gap.setSourceType(EvidenceType.CUSTOMER_DOCUMENT.name());
        gap.setStatus(status);
        gap.setImpact(impact);
        gap.setSuggestedAction(suggestedAction);
        return gap;
    }

    private AgentSupplementRequest supplement(String dataCode, String dataName, String reason, String action) {
        AgentSupplementRequest request = new AgentSupplementRequest();
        request.setDataCode(dataCode);
        request.setDataName(dataName);
        request.setReason(reason);
        request.setSuggestedAction(action);
        return request;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AgentDefinition buildDefinition() {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode(AGENT_CODE);
        definition.setAgentName("材料识别 Agent");
        definition.setDescription("基于 Mock 材料源完成材料分类、必备材料完整性、重复与时效检查，并登记客户材料证据");
        definition.setVersion("1.0.0");
        definition.setPromptVersion("rule-1.0");
        definition.setDependencies(List.of());
        definition.setRequiredTools(List.of(TOOL_LIST_MATERIALS, TOOL_CLASSIFY_DOCUMENT, TOOL_CREATE_EVIDENCE));
        definition.setEnabled(true);
        definition.setSupportsIncrementalExecution(true);
        return definition;
    }

    private record RequiredDocument(String docType, String name) {
    }

    private record RecognizedMaterial(DocumentMaterialSnapshot material, DocumentClassification classification) {
    }
}
