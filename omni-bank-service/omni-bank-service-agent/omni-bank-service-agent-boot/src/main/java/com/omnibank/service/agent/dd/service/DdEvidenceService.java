package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceDetailVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceListVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceQueryReq;
import java.util.List;

/**
 * 证据中心服务：统一登记外部采集证据并提供溯源查询
 */
public interface DdEvidenceService {

    /**
     * 采集任务的基础外部证据（工商/司法/征信），返回任务当前全部证据ID。
     * 同一任务重复执行不会重复入库，采集结果全部带 isMock 标记。
     */
    List<Long> collectBaseEvidence(Long taskId);

    /** 任务证据分页：按类型、来源类型与来源系统过滤 */
    IPage<EvidenceListVO> page(Long taskId, EvidenceQueryReq req);

    /** 证据详情，不存在抛业务异常 */
    EvidenceDetailVO detail(Long evidenceId);
}
