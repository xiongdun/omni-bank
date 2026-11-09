package com.omnibank.service.agent.dd.service;

import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;

/**
 * 授信申请服务：一个尽调任务只对应一条申请记录
 */
public interface DdLoanApplicationService {

    /** 按任务查询申请，不存在返回 null */
    DdLoanApplication findByTaskId(Long taskId);

    /** 按任务查询申请，不存在抛业务异常 */
    DdLoanApplication requireByTaskId(Long taskId);

    /** 创建申请记录并保存原始快照 */
    DdLoanApplication create(Long taskId, LoanApplicationReq req, String snapshot);
}
