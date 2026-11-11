package com.omnibank.service.agent.dd.tool.impl;

/**
 * 工具登记的凭证引用：工具层只回传证据标识与来源，Agent 负责转换为 EvidenceRef
 */
public record ToolEvidenceRef(Long evidenceId, String evidenceType, String sourceSystem,
                              String sourceReference, String dataPeriod, String excerpt) {
}
