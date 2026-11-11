package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 现场调查分析输出：现场观察事实（FACT）与访谈陈述（STATEMENT）分离，并给出地址与偏差比对结论
 */
@Data
public class SiteVisitData {

    private String enterpriseName;

    private String creditCode;

    private String registeredAddress;

    private String siteAddress;

    /** 现场地址与工商注册地址是否一致 */
    private Boolean addressConsistent;

    // ===== 现场观察事实（FACT） =====

    private String investigationDate;

    private String productionStatus;

    private Integer onSiteStaffCount;

    /** 设备开机率（%） */
    private BigDecimal equipmentOperatingRate;

    private String inventoryObservation;

    private Integer photoCount;

    private String investigator;

    /** 数据性质标识：FACT */
    private String observationNature;

    private String observationEvidenceId;

    // ===== 访谈陈述（STATEMENT） =====

    private String interviewDate;

    /** 访谈声称的产能利用率（%） */
    private BigDecimal capacityUtilizationRate;

    private String orderBacklog;

    private Integer interviewEmployeeCount;

    private String mainCustomersStatement;

    private String mainSuppliersStatement;

    private String interviewee;

    /** 数据性质标识：STATEMENT */
    private String statementNature;

    private String statementEvidenceId;

    /** 访谈产能利用率与设备开机率偏差（%） */
    private BigDecimal capacityDeviationRate;

    /** 访谈员工人数与现场在岗人数偏差（%） */
    private BigDecimal staffDeviationRate;

    /** 可引用的证据数量 */
    private Integer evidenceCount;
}
