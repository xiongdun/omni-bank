package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.entity.DdEnterprise;

/**
 * 企业主体服务
 */
public interface DdEnterpriseService {

    /** 按主键查询，不存在抛业务异常 */
    DdEnterprise getById(Long id);

    /** 按统一社会信用代码复用企业主体，不存在则创建；并发冲突时回查 */
    DdEnterprise getOrCreate(EnterpriseReq req);
}
