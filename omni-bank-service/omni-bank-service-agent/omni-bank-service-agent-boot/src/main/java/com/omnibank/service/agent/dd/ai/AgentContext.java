package com.omnibank.service.agent.dd.ai;

import com.omnibank.service.agent.dd.dto.application.LoanApplicationVO;
import com.omnibank.service.agent.dd.dto.enterprise.EnterpriseVO;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Agent 执行上下文：只装配与当前 Agent 职责相关的数据，避免上下文膨胀与数据越权
 */
@Data
public class AgentContext {

    /** 尽调任务ID */
    private Long taskId;

    /** 企业ID */
    private Long enterpriseId;

    /** 本次执行ID */
    private String executionId;

    /** 业务场景 */
    private String businessScenario;

    /** 子场景 */
    private String subScenario;

    /** 企业主体信息 */
    private EnterpriseVO enterpriseProfile;

    /** 授信申请信息 */
    private LoanApplicationVO creditApplication;

    /** 可用证据引用 */
    private List<EvidenceRef> evidences;

    /** 上游节点结果（Graph 节点ID -> AgentResult） */
    private Map<String, Object> upstreamResults;

    /** 可用工具编码 */
    private List<String> availableTools;

    /** 输入数据版本（数据编码 -> 版本） */
    private Map<String, String> inputVersions;

    /** 链路追踪 ID */
    private String traceId;

    /** 是否 Mock 模式：为 true 时所有外部数据均来自 Mock Adapter */
    private boolean mockMode;
}
