package com.omnibank.service.agent.dd.adapter;

/**
 * 外部数据采集请求：只携带已确认的企业主体标识，不包含银行内部敏感信息
 */
public record ExternalDataQuery(Long taskId, Long enterpriseId, String creditCode, String enterpriseName,
                                String legalPerson, String industryName) {
}
