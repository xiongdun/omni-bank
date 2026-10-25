package com.omnibank.service.credit.mortgage.application.service;

import org.springframework.stereotype.Service;

/**
 * 抵押场景应用服务：用例编排入口，web/rpc 适配层统一调用本层。
 * 探活统一走 /actuator/health，应用服务不承载探活逻辑。
 * TODO: 后续按业务编排领域模型与仓储（抵押物登记/评估/解押）。
 */
@Service
public class MortgageApplicationService {
}
