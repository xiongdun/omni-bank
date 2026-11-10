package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 证据来源类型：与证据类型（EvidenceType）区分，同一类证据可能来自不同来源
 */
@Getter
@AllArgsConstructor
public enum EvidenceSourceType {

    BANK_INTERFACE("银行接口"),
    EXTERNAL_DATA("外部数据接口"),
    CUSTOMER_UPLOAD("客户上传文件"),
    OCR("OCR 结果"),
    FINANCIAL_CALCULATION("财务计算结果"),
    INDUSTRY_RESEARCH("行业研究资料"),
    POLICY_DOCUMENT("银行制度文件"),
    MANUAL_INVESTIGATION("人工调查记录");

    private final String desc;
}
