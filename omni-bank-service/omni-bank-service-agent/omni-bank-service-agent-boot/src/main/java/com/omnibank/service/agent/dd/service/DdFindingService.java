package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.ai.AgentFinding;
import com.omnibank.service.agent.dd.dto.finding.FindingListVO;
import com.omnibank.service.agent.dd.dto.finding.FindingQueryReq;
import java.util.List;

/**
 * 风险线索服务：Agent 结果落库与任务维度检索
 */
public interface DdFindingService {

    /**
     * 用本次执行结果替换该 Agent 的线索集：先逻辑删除旧线索，再写入新线索，
     * 保证重跑后查询到的是当前分析结论而不是历史叠加。
     */
    void replaceByAgent(Long taskId, Long planId, Long executionId, String agentCode, String nodeId,
                        List<AgentFinding> findings);

    /** 任务线索分页：按 Agent、分类、严重程度与状态过滤 */
    IPage<FindingListVO> page(Long taskId, FindingQueryReq req);

    /** 引用指定证据的线索，按创建时间倒序 */
    List<FindingListVO> listByEvidence(Long taskId, Long evidenceId);
}
