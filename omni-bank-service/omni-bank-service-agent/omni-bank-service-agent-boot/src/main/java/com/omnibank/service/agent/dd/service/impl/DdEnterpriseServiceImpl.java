package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.entity.DdEnterprise;
import com.omnibank.service.agent.dd.mapper.DdEnterpriseMapper;
import com.omnibank.service.agent.dd.service.DdEnterpriseService;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 企业主体服务实现：唯一性由 uk_credit_code 保证，先查后插只用于减少冲突，最终以唯一键冲突回查兜底
 */
@Service
public class DdEnterpriseServiceImpl extends ServiceImpl<DdEnterpriseMapper, DdEnterprise>
        implements DdEnterpriseService {

    @Override
    public DdEnterprise getById(Long id) {
        DdEnterprise enterprise = super.getById(id);
        if (enterprise == null) {
            throw new BizException(DdErrorCode.ENTERPRISE_NOT_FOUND);
        }
        return enterprise;
    }

    @Override
    public DdEnterprise getOrCreate(EnterpriseReq req) {
        DdEnterprise existing = findByCreditCode(req.getCreditCode());
        if (existing != null) {
            return existing;
        }
        if (!StringUtils.hasText(req.getEnterpriseName())) {
            throw new BizException(DdErrorCode.ENTERPRISE_IDENTITY_MISSING,
                    "企业主体不存在，创建时企业名称不能为空");
        }
        DdEnterprise enterprise = new DdEnterprise();
        enterprise.setCreditCode(req.getCreditCode());
        enterprise.setCustomerId(req.getCustomerId());
        enterprise.setEnterpriseName(req.getEnterpriseName());
        enterprise.setDataSource("MANUAL_INPUT");
        enterprise.setLastSyncAt(LocalDateTime.now());
        enterprise.setVersion(0);
        try {
            save(enterprise);
            return enterprise;
        } catch (DuplicateKeyException e) {
            DdEnterprise concurrent = findByCreditCode(req.getCreditCode());
            if (concurrent == null) {
                throw new BizException(DdErrorCode.ENTERPRISE_CONFLICT);
            }
            return concurrent;
        }
    }

    private DdEnterprise findByCreditCode(String creditCode) {
        return lambdaQuery().eq(DdEnterprise::getCreditCode, creditCode).one();
    }
}
