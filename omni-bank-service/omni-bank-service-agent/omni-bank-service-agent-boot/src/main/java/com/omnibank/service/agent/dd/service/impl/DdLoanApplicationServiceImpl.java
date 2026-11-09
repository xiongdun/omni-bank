package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;
import com.omnibank.service.agent.dd.mapper.DdLoanApplicationMapper;
import com.omnibank.service.agent.dd.service.DdLoanApplicationService;
import org.springframework.stereotype.Service;

/**
 * 授信申请服务实现
 */
@Service
public class DdLoanApplicationServiceImpl extends ServiceImpl<DdLoanApplicationMapper, DdLoanApplication>
        implements DdLoanApplicationService {

    @Override
    public DdLoanApplication findByTaskId(Long taskId) {
        return lambdaQuery().eq(DdLoanApplication::getTaskId, taskId).one();
    }

    @Override
    public DdLoanApplication requireByTaskId(Long taskId) {
        DdLoanApplication application = findByTaskId(taskId);
        if (application == null) {
            throw new BizException(DdErrorCode.LOAN_APPLICATION_NOT_FOUND);
        }
        return application;
    }

    @Override
    public DdLoanApplication create(Long taskId, LoanApplicationReq req, String snapshot) {
        DdLoanApplication application = new DdLoanApplication();
        application.setTaskId(taskId);
        application.setApplicationNo(req.getApplicationNo());
        application.setApplicationType(req.getApplicationType());
        application.setProductCode(req.getProductCode());
        application.setProductName(req.getProductName());
        application.setApplyAmount(req.getApplyAmount());
        application.setCurrency(req.getCurrency());
        application.setApplyTerm(req.getApplyTerm());
        application.setTermUnit(req.getTermUnit());
        application.setLoanPurpose(req.getLoanPurpose());
        application.setRepaymentMethod(req.getRepaymentMethod());
        application.setGuaranteeType(req.getGuaranteeType());
        application.setApplicationSnapshot(snapshot);
        application.setVersion(0);
        save(application);
        return application;
    }
}
