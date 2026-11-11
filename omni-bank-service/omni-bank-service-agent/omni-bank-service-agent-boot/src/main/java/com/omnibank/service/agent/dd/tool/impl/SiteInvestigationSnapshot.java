package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;

/**
 * 现场调查记录快照：字段均为客户经理现场观察事实（FACT），不代表已核实的经营结论
 */
public record SiteInvestigationSnapshot(Long evidenceId, String sourceSystem, String sourceReference,
                                        String dataPeriod, String contentSummary, String investigationDate,
                                        String siteAddress, String productionStatus, int onSiteStaffCount,
                                        BigDecimal equipmentOperatingRate, String inventoryObservation,
                                        int photoCount, String investigator, String dataNature) {
}
