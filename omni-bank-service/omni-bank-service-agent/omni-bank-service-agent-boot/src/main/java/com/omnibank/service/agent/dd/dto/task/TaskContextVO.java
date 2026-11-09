package com.omnibank.service.agent.dd.dto.task;

import com.omnibank.common.core.vo.BaseVO;
import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.dto.enterprise.EnterpriseVO;
import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业上下文摘要视图：只提供必要业务信息与证据索引，不包含大体积原始数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskContextVO extends BaseVO {

    private Long taskId;

    private Long enterpriseId;

    /** 银行企业客户编号 */
    private String customerId;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 上下文版本 */
    private Integer contextVersion;

    /** 分析版本 */
    private Integer analysisVersion;

    /** 企业主体信息 */
    private EnterpriseVO enterpriseInfo;

    /** 授信申请信息 */
    private LoanApplicationVO loanApplication;

    /** 最新业务场景识别结果 */
    private ScenarioVO businessScenario;

    /** 证据ID集合 */
    private List<String> evidenceIds;
}
