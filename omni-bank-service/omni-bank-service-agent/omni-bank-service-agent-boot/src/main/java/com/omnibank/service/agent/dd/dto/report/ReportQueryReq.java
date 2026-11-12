package com.omnibank.service.agent.dd.dto.report;

import com.omnibank.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告版本分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportQueryReq extends PageQuery {

    /** 报告状态，为空不过滤 */
    private String reportStatus;
}
