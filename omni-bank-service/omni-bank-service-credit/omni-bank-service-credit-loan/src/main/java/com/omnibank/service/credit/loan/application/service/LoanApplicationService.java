package com.omnibank.service.credit.loan.application.service;

import org.springframework.stereotype.Service;

/**
 * 借据场景应用服务：用例编排入口，web/rpc 适配层统一调用本层。
 * 探活统一走 /actuator/health，应用服务不承载探活逻辑。
 * TODO: 后续按业务编排领域模型与仓储（借据状态机/还款计划生成/计息）。
 */
@Service
public class LoanApplicationService {
}
