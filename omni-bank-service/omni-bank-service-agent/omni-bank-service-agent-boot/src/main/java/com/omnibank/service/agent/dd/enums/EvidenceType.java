package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 证据类型：模块一只建索引结构，文件上传与解析在 P2 扩展
 */
@Getter
@AllArgsConstructor
public enum EvidenceType {

    ENTERPRISE_PROFILE("企业主体信息"),
    FINANCIAL_STATEMENT("财务报表"),
    CREDIT_REPORT("征信报告"),
    JUDICIAL_RECORD("司法记录"),
    SITE_INVESTIGATION("现场调查"),
    CUSTOMER_DOCUMENT("客户上传材料"),
    CALCULATION("系统计算结果"),
    POLICY_DOCUMENT("制度文件"),
    INDUSTRY_RESEARCH("行业研究"),
    MANUAL_INPUT("人工录入");

    private final String desc;
}
