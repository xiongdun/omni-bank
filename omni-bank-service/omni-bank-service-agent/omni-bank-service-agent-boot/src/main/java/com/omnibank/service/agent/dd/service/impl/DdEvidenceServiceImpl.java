package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.adapter.ExternalDataAdapter;
import com.omnibank.service.agent.dd.adapter.ExternalDataItem;
import com.omnibank.service.agent.dd.adapter.ExternalDataQuery;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceDetailVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceListVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceQueryReq;
import com.omnibank.service.agent.dd.entity.DdEnterprise;
import com.omnibank.service.agent.dd.entity.DdEvidence;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.EvidenceStatus;
import com.omnibank.service.agent.dd.mapper.DdEvidenceMapper;
import com.omnibank.service.agent.dd.service.DdEnterpriseService;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 证据中心实现：适配器只负责取数，落库、去重与查询统一收敛在证据中心
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdEvidenceServiceImpl extends ServiceImpl<DdEvidenceMapper, DdEvidence>
        implements DdEvidenceService {

    private final DdTaskService taskService;

    private final DdEnterpriseService enterpriseService;

    private final List<ExternalDataAdapter> adapters;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> collectBaseEvidence(Long taskId) {
        DdTask task = taskService.requireTask(taskId);
        DdEnterprise enterprise = enterpriseService.getById(task.getEnterpriseId());
        ExternalDataQuery query = new ExternalDataQuery(taskId, enterprise.getId(), enterprise.getCreditCode(),
                enterprise.getEnterpriseName(), enterprise.getLegalPerson(), enterprise.getIndustryName());

        for (ExternalDataAdapter adapter : adapters) {
            List<ExternalDataItem> items;
            try {
                items = adapter.collect(query);
            } catch (Exception e) {
                throw new BizException(DdErrorCode.EVIDENCE_COLLECT_FAILED,
                        "外部数据适配器 " + adapter.name() + " 采集失败");
            }
            for (ExternalDataItem item : items) {
                if (exists(taskId, item)) {
                    continue;
                }
                save(toEntity(taskId, enterprise.getId(), item, adapter.mock()));
            }
        }

        List<Long> evidenceIds = lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .orderByAsc(DdEvidence::getId)
                .list()
                .stream()
                .map(DdEvidence::getId)
                .toList();
        log.info("任务证据采集完成 taskId={}, evidenceCount={}", taskId, evidenceIds.size());
        return evidenceIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordEvidence(Long taskId, Long enterpriseId, ExternalDataItem item) {
        DdEvidence existing = lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .eq(DdEvidence::getSourceSystem, item.sourceSystem())
                .eq(DdEvidence::getSourceReference, item.sourceReference())
                .last("LIMIT 1")
                .one();
        if (existing != null) {
            return existing.getId();
        }
        DdEvidence evidence = toEntity(taskId, enterpriseId, item, item.mock());
        save(evidence);
        return evidence.getId();
    }

    @Override
    public IPage<EvidenceListVO> page(Long taskId, EvidenceQueryReq req) {
        taskService.requireTask(taskId);
        return lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .eq(StringUtils.hasText(req.getEvidenceType()), DdEvidence::getEvidenceType, req.getEvidenceType())
                .eq(StringUtils.hasText(req.getSourceType()), DdEvidence::getSourceType, req.getSourceType())
                .eq(StringUtils.hasText(req.getSourceSystem()), DdEvidence::getSourceSystem, req.getSourceSystem())
                .orderByDesc(DdEvidence::getCollectedAt)
                .orderByDesc(DdEvidence::getId)
                .page(new Page<>(req.getCurrent(), req.getSize()))
                .convert(this::toListVO);
    }

    @Override
    public EvidenceDetailVO detail(Long evidenceId) {
        DdEvidence entity = getById(evidenceId);
        if (entity == null) {
            throw new BizException(DdErrorCode.EVIDENCE_NOT_FOUND);
        }
        return toDetailVO(entity);
    }

    /** 以任务 + 来源系统 + 来源引用作为幂等键，重复采集直接跳过 */
    private boolean exists(Long taskId, ExternalDataItem item) {
        return lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .eq(DdEvidence::getSourceSystem, item.sourceSystem())
                .eq(DdEvidence::getSourceReference, item.sourceReference())
                .count() > 0;
    }

    private DdEvidence toEntity(Long taskId, Long enterpriseId, ExternalDataItem item, boolean adapterMock) {
        DdEvidence evidence = new DdEvidence();
        evidence.setTaskId(taskId);
        evidence.setEnterpriseId(enterpriseId);
        evidence.setEvidenceType(item.evidenceType());
        evidence.setSourceType(item.sourceType());
        evidence.setSourceSystem(item.sourceSystem());
        evidence.setSourceReference(item.sourceReference());
        evidence.setSubjectId(item.subjectId());
        evidence.setDataPeriod(item.dataPeriod());
        evidence.setCollectedAt(item.collectedAt());
        evidence.setContentSummary(item.contentSummary());
        evidence.setContentHash(item.contentHash());
        evidence.setConfidence(item.confidence());
        evidence.setIsMock(adapterMock || item.mock());
        evidence.setStatus(EvidenceStatus.ACTIVE.name());
        evidence.setVersion(0);
        return evidence;
    }

    private EvidenceListVO toListVO(DdEvidence entity) {
        EvidenceListVO vo = new EvidenceListVO();
        vo.setEvidenceId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setEvidenceType(entity.getEvidenceType());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceSystem(entity.getSourceSystem());
        vo.setCollectedAt(entity.getCollectedAt());
        vo.setIsMock(entity.getIsMock());
        vo.setContentSummary(entity.getContentSummary());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private EvidenceDetailVO toDetailVO(DdEvidence entity) {
        EvidenceDetailVO vo = new EvidenceDetailVO();
        vo.setEvidenceId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setEnterpriseId(entity.getEnterpriseId());
        vo.setEvidenceType(entity.getEvidenceType());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceSystem(entity.getSourceSystem());
        vo.setSourceReference(entity.getSourceReference());
        vo.setSubjectId(entity.getSubjectId());
        vo.setDataPeriod(entity.getDataPeriod());
        vo.setCollectedAt(entity.getCollectedAt());
        vo.setContentSummary(entity.getContentSummary());
        vo.setContentHash(entity.getContentHash());
        vo.setConfidence(entity.getConfidence());
        vo.setIsMock(entity.getIsMock());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
