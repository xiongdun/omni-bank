package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;

/**
 * 材料分类结果：docType 为 MaterialDocType 编码，basis 记录命中的关键字以便复核
 */
public record DocumentClassification(String docType, String docTypeName, BigDecimal confidence, String basis) {
}
