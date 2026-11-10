package com.omnibank.service.agent.dd.dto.evidence;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务证据分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvidenceQueryReq extends PageQuery {

    /** 证据类型，见 EvidenceType */
    private String evidenceType;

    /** 来源类型，见 EvidenceSourceType */
    private String sourceType;

    /** 来源系统 */
    private String sourceSystem;
}
