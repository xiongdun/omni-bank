package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 材料识别输出：材料清单、分类结果、必备材料完备度与证据数量，所有内容均来自 Mock 材料源
 */
@Data
public class DocumentRecognitionData {

    /** 材料总份数 */
    private Integer materialCount;

    /** 材料明细 */
    private List<MaterialSummary> materials;

    /** 已识别的材料类型编码 */
    private List<String> recognizedDocTypes;

    /** 缺失的必备材料名称 */
    private List<String> missingDocuments;

    /** 已识别必备材料数 / 必备材料总数（0-1） */
    private BigDecimal evidenceCompleteness;

    /** 必备材料是否齐全 */
    private Boolean requiredMaterialsComplete;

    /** 已登记的材料证据数量 */
    private Integer evidenceCount;

    /** 材料条目摘要 */
    @Data
    public static class MaterialSummary {

        private String fileName;

        private String docType;

        private String docTypeName;

        private BigDecimal confidence;

        /** 材料是否已过期（依据 Mock 有效期字段） */
        private Boolean expired;
    }
}
