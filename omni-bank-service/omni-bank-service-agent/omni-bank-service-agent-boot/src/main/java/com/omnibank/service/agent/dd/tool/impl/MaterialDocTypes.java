package com.omnibank.service.agent.dd.tool.impl;

/**
 * 材料类型编码：分类工具输出与 Agent 必备材料规则共用，避免字符串散落在多处
 */
public final class MaterialDocTypes {

    public static final String BUSINESS_LICENSE = "BUSINESS_LICENSE";

    public static final String ARTICLES_OF_ASSOCIATION = "ARTICLES_OF_ASSOCIATION";

    public static final String FINANCIAL_STATEMENT = "FINANCIAL_STATEMENT";

    public static final String BANK_STATEMENT = "BANK_STATEMENT";

    public static final String TAX_CERTIFICATE = "TAX_CERTIFICATE";

    public static final String CREDIT_AUTHORIZATION = "CREDIT_AUTHORIZATION";

    public static final String SALES_CONTRACT = "SALES_CONTRACT";

    public static final String GUARANTEE_CONTRACT = "GUARANTEE_CONTRACT";

    public static final String PROPERTY_CERTIFICATE = "PROPERTY_CERTIFICATE";

    public static final String UNRECOGNIZED = "UNRECOGNIZED";

    private MaterialDocTypes() {
    }
}
