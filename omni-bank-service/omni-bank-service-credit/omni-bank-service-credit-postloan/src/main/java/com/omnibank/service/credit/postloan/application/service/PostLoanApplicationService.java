package com.omnibank.service.credit.postloan.application.service;

import org.springframework.stereotype.Service;

/**
 * 贷后场景应用服务：用例编排入口，web/rpc 适配层统一调用本层。
 * 探活统一走 /actuator/health，应用服务不承载探活逻辑。
 * TODO: 后续按业务编排领域模型与仓储（逾期认定/催收分案/贷后监控）。
 */
@Service
public class PostLoanApplicationService {
}
