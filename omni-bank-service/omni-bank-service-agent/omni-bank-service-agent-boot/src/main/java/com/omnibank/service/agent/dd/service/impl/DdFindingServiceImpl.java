package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.omnibank.service.agent.dd.ai.AgentFinding;
import com.omnibank.service.agent.dd.ai.EvidenceRef;
import com.omnibank.service.agent.dd.dto.finding.FindingListVO;
import com.omnibank.service.agent.dd.dto.finding.FindingQueryReq;
import com.omnibank.service.agent.dd.entity.DdFinding;
import com.omnibank.service.agent.dd.mapper.DdFindingMapper;
import com.omnibank.service.agent.dd.service.DdFindingService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 风险线索实现：按任务 + Agent 覆盖线索集，线索编码缺失时自动编号保证可追溯
 */
@Service
@RequiredArgsConstructor
public class DdFindingServiceImpl
        extends ServiceImpl<DdFindingMapper, DdFinding>
        implements DdFindingService {

    private static final TypeReference<List<EvidenceRef>> EVIDENCE_REFS_TYPE =
            new TypeReference<List<EvidenceRef>>() {
            };

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<List<String>>() {
            };

    private final DdJsonSupport jsonSupport;

    private final DdTaskService taskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceByAgent(Long taskId, Long planId, Long executionId, String agentCode, String nodeId,
                               List<AgentFinding> findings) {
        remove(Wrappers.<DdFinding>lambdaQuery()
                .eq(DdFinding::getTaskId, taskId)
                .eq(DdFinding::getAgentCode, agentCode));
        if (findings == null || findings.isEmpty()) {
            return;
        }
        Map<String, AgentFinding> deduplicated = new LinkedHashMap<>();
        for (int index = 0; index < findings.size(); index++) {
            AgentFinding finding = findings.get(index);
            if (finding == null) {
                continue;
            }
            String findingCode = StringUtils.hasText(finding.getFindingCode())
                    ? finding.getFindingCode() : "FND-" + executionId + "-" + (index + 1);
            deduplicated.putIfAbsent(findingCode, finding);
        }
        deduplicated.forEach((findingCode, finding) -> save(toEntity(taskId, planId, executionId, agentCode,
                nodeId, findingCode, finding)));
    }

    @Override
    public IPage<FindingListVO> page(Long taskId, FindingQueryReq req) {
        taskService.requireTask(taskId);
        return lambdaQuery()
                .eq(DdFinding::getTaskId, taskId)
                .eq(StringUtils.hasText(req.getAgentCode()), DdFinding::getAgentCode, req.getAgentCode())
                .eq(StringUtils.hasText(req.getCategory()), DdFinding::getCategory, req.getCategory())
                .eq(StringUtils.hasText(req.getSeverity()), DdFinding::getSeverity, req.getSeverity())
                .eq(StringUtils.hasText(req.getStatus()), DdFinding::getStatus, req.getStatus())
                .orderByDesc(DdFinding::getId)
                .page(new Page<>(req.getCurrent(), req.getSize()))
                .convert(this::toVO);
    }

    @Override
    public List<FindingListVO> listByEvidence(Long taskId, Long evidenceId) {
        return lambdaQuery()
                .eq(DdFinding::getTaskId, taskId)
                .like(DdFinding::getEvidenceRefs, "\"evidenceId\":\"" + evidenceId + "\"")
                .orderByDesc(DdFinding::getId)
                .list()
                .stream()
                .map(this::toVO)
                .toList();
    }

    private DdFinding toEntity(Long taskId, Long planId, Long executionId, String agentCode, String nodeId,
                               String findingCode, AgentFinding finding) {
        DdFinding entity = new DdFinding();
        entity.setTaskId(taskId);
        entity.setPlanId(planId);
        entity.setExecutionId(executionId);
        entity.setAgentCode(agentCode);
        entity.setNodeId(nodeId);
        entity.setFindingCode(truncate(findingCode, 64));
        entity.setCategory(truncate(finding.getCategory(), 64));
        entity.setSeverity(truncate(finding.getSeverity(), 32));
        entity.setTitle(truncate(finding.getTitle(), 256));
        entity.setDescription(truncate(finding.getDescription(), 4000));
        entity.setConfidence(finding.getConfidence());
        entity.setStatus(StringUtils.hasText(finding.getStatus()) ? finding.getStatus() : "UNKNOWN");
        entity.setEvidenceRefs(finding.getEvidenceRefs() == null
                ? null : jsonSupport.write(finding.getEvidenceRefs()));
        entity.setRelatedFindingCodes(finding.getRelatedFindingCodes() == null
                ? null : jsonSupport.write(finding.getRelatedFindingCodes()));
        entity.setVerificationSuggestion(truncate(finding.getVerificationSuggestion(), 1000));
        entity.setVersion(0);
        return entity;
    }

    private FindingListVO toVO(DdFinding entity) {
        FindingListVO vo = new FindingListVO();
        vo.setFindingId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setPlanId(entity.getPlanId());
        vo.setExecutionId(entity.getExecutionId());
        vo.setAgentCode(entity.getAgentCode());
        vo.setNodeId(entity.getNodeId());
        vo.setFindingCode(entity.getFindingCode());
        vo.setCategory(entity.getCategory());
        vo.setSeverity(entity.getSeverity());
        vo.setTitle(entity.getTitle());
        vo.setDescription(entity.getDescription());
        vo.setConfidence(entity.getConfidence());
        vo.setStatus(entity.getStatus());
        vo.setEvidenceRefs(jsonSupport.read(entity.getEvidenceRefs(), EVIDENCE_REFS_TYPE));
        vo.setRelatedFindingCodes(jsonSupport.read(entity.getRelatedFindingCodes(), STRING_LIST_TYPE));
        vo.setVerificationSuggestion(entity.getVerificationSuggestion());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
