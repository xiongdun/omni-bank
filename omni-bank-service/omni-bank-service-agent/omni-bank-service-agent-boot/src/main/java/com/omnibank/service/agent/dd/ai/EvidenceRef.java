package com.omnibank.service.agent.dd.ai;

import lombok.Data;

/**
 * 证据引用：结论必须能追溯到原始数据来源与数据期间
 */
@Data
public class EvidenceRef {

    /** 证据ID */
    private String evidenceId;

    /** 证据类型 */
    private String evidenceType;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源引用 */
    private String sourceReference;

    /** 数据期间 */
    private String dataPeriod;

    /** 引用片段/摘要 */
    private String excerpt;
}
