package com.omnibank.service.agent.dd.tool.impl;

/**
 * 工商登记证据快照：工具层只做取数与标准化，不在此处做业务判断
 */
public record EnterpriseRegistrySnapshot(Long evidenceId, String contentSummary, String sourceSystem,
                                         String sourceReference, String dataPeriod, boolean mock) {
}
