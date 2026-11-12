package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.ai.AgentSupplementRequest;
import com.omnibank.service.agent.dd.dto.supplement.CompleteSupplementReq;
import com.omnibank.service.agent.dd.dto.supplement.CreateSupplementReq;
import com.omnibank.service.agent.dd.dto.supplement.SupplementQueryReq;
import com.omnibank.service.agent.dd.dto.supplement.SupplementRequestVO;
import java.util.List;

/**
 * 补充材料请求服务：Agent 结果落库、手工创建、完成与待办查询
 */
public interface DdSupplementRequestService {

    /** 将 Agent 结果中的补充资料请求落库，同一任务同一数据编码已存在待补充请求时跳过 */
    void recordFromExecution(Long taskId, Long planId, Long executionId, String agentCode,
                             List<AgentSupplementRequest> requests);

    /** 任务补充材料请求分页 */
    IPage<SupplementRequestVO> page(Long taskId, SupplementQueryReq req);

    /** 手工创建补充材料请求 */
    SupplementRequestVO create(Long taskId, CreateSupplementReq req);

    /** 完成补充材料请求，返回完成后的记录 */
    SupplementRequestVO complete(Long taskId, Long requestId, CompleteSupplementReq req);

    /** 任务待补充请求数量 */
    long countPending(Long taskId);

    /** 存在待补充请求的 Agent 编码 */
    List<String> pendingAgentCodes(Long taskId);
}
