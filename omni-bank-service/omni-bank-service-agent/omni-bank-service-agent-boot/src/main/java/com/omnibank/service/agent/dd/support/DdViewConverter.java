package com.omnibank.service.agent.dd.support;

import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.dto.enterprise.EnterpriseVO;
import com.omnibank.service.agent.dd.entity.DdEnterprise;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

/**
 * 尽调域实体到视图的转换
 */
@Component
public class DdViewConverter {

    public EnterpriseVO toEnterpriseVO(DdEnterprise entity) {
        if (entity == null) {
            return null;
        }
        EnterpriseVO vo = new EnterpriseVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    public LoanApplicationVO toApplicationVO(DdLoanApplication entity) {
        if (entity == null) {
            return null;
        }
        LoanApplicationVO vo = new LoanApplicationVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
