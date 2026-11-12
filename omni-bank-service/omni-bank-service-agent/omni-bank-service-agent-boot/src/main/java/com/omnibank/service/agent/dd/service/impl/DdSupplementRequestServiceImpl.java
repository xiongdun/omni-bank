package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.ai.AgentSupplementRequest;
import com.omnibank.service.agent.dd.dto.supplement.CompleteSupplementReq;
import com.omnibank.service.agent.dd.dto.supplement.CreateSupplementReq;
import com.omnibank.service.agent.dd.dto.supplement.SupplementQueryReq;
import com.omnibank.service.agent.dd.dto.supplement.SupplementRequestVO;
import com.omnibank.service.agent.dd.entity.DdSupplementRequest;
import com.omnibank.service.agent.dd.enums.SupplementRequestSource;
import com.omnibank.service.agent.dd.enums.SupplementRequestStatus;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.event.DdReanalysisRequestedEvent;
import com.omnibank.service.agent.dd.mapper.DdSupplementRequestMapper;
import com.omnibank.service.agent.dd.service.DdSupplementRequestService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 补充材料请求实现：按任务 + 数据编码去重，完成后由调用方触发增量重分析
 */
@Service
@RequiredArgsConstructor
public class DdSupplementRequestServiceImpl
        extends ServiceImpl<DdSupplementRequestMapper, DdSupplementRequest>
        implements DdSupplementRequestService {

    private final DdTaskService taskService;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFromExecution(Long taskId, Long planId, Long executionId, String agentCode,
                                    List<AgentSupplementRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (AgentSupplementRequest request : requests) {
            if (request == null || !StringUtils.hasText(request.getDataCode())) {
                continue;
            }
            if (existsPending(taskId, request.getDataCode())) {
                continue;
            }
            DdSupplementRequest entity = new DdSupplementRequest();
            entity.setTaskId(taskId);
            entity.setPlanId(planId);
            entity.setExecutionId(executionId);
            entity.setAgentCode(agentCode);
            entity.setDataCode(truncate(request.getDataCode(), 64));
            entity.setDataName(truncate(request.getDataName(), 256));
            entity.setReason(truncate(request.getReason(), 1000));
            entity.setSuggestedAction(truncate(request.getSuggestedAction(), 1000));
            entity.setStatus(SupplementRequestStatus.PENDING.name());
            entity.setSource(SupplementRequestSource.AGENT.name());
            entity.setVersion(0);
            save(entity);
        }
    }

    @Override
    public IPage<SupplementRequestVO> page(Long taskId, SupplementQueryReq req) {
        taskService.requireTask(taskId);
        return lambdaQuery()
                .eq(DdSupplementRequest::getTaskId, taskId)
                .eq(StringUtils.hasText(req.getAgentCode()), DdSupplementRequest::getAgentCode, req.getAgentCode())
                .eq(StringUtils.hasText(req.getStatus()), DdSupplementRequest::getStatus, req.getStatus())
                .eq(StringUtils.hasText(req.getSource()), DdSupplementRequest::getSource, req.getSource())
                .orderByDesc(DdSupplementRequest::getId)
                .page(new Page<>(req.getCurrent(), req.getSize()))
                .convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplementRequestVO create(Long taskId, CreateSupplementReq req) {
        taskService.requireTask(taskId);
        DdSupplementRequest entity = new DdSupplementRequest();
        entity.setTaskId(taskId);
        entity.setAgentCode(truncate(req.getAgentCode(), 64));
        entity.setDataCode(truncate(req.getDataCode(), 64));
        entity.setDataName(truncate(req.getDataName(), 256));
        entity.setReason(truncate(req.getReason(), 1000));
        entity.setSuggestedAction(truncate(req.getSuggestedAction(), 1000));
        entity.setStatus(SupplementRequestStatus.PENDING.name());
        entity.setSource(SupplementRequestSource.MANUAL.name());
        entity.setVersion(0);
        save(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplementRequestVO complete(Long taskId, Long requestId, CompleteSupplementReq req) {
        taskService.requireTask(taskId);
        DdSupplementRequest request = lambdaQuery()
                .eq(DdSupplementRequest::getId, requestId)
                .eq(DdSupplementRequest::getTaskId, taskId)
                .one();
        if (request == null) {
            throw new BizException(DdErrorCode.SUPPLEMENT_REQUEST_NOT_FOUND);
        }
        if (!SupplementRequestStatus.PENDING.name().equals(request.getStatus())) {
            throw new BizException(DdErrorCode.SUPPLEMENT_REQUEST_STATUS_NOT_ALLOWED,
                    "补充材料请求当前状态「" + request.getStatus() + "」不允许完成操作");
        }
        LocalDateTime now = LocalDateTime.now();
        DdSupplementRequest update = new DdSupplementRequest();
        update.setStatus(SupplementRequestStatus.COMPLETED.name());
        update.setRemark(truncate(req.getRemark(), 1000));
        update.setCompletedAt(now);
        update.setVersion(request.getVersion() + 1);
        boolean updated = update(update, Wrappers.<DdSupplementRequest>lambdaUpdate()
                .eq(DdSupplementRequest::getId, request.getId())
                .eq(DdSupplementRequest::getVersion, request.getVersion()));
        if (!updated) {
            throw new BizException(DdErrorCode.TASK_STATUS_CONFLICT, "补充材料请求已被并发修改，请刷新后重试");
        }
        request.setStatus(update.getStatus());
        request.setRemark(update.getRemark());
        request.setCompletedAt(now);
        request.setVersion(request.getVersion() + 1);
        publishReanalysis(request);
        return toVO(request);
    }

    /** 完成补充后触发增量重分析；任务不在补充/退回状态时不强触发 */
    private void publishReanalysis(DdSupplementRequest request) {
        String taskStatus = taskService.requireTask(request.getTaskId()).getTaskStatus();
        if (!TaskStatus.WAITING_FOR_SUPPLEMENT.name().equals(taskStatus)
                && !TaskStatus.RETURNED.name().equals(taskStatus)) {
            return;
        }
        Set<String> agents = StringUtils.hasText(request.getAgentCode())
                ? Set.of(request.getAgentCode()) : Set.of();
        eventPublisher.publishEvent(new DdReanalysisRequestedEvent(
                request.getTaskId(), agents, "补充材料完成：" + request.getDataCode()));
    }

    @Override
    public long countPending(Long taskId) {
        return lambdaQuery()
                .eq(DdSupplementRequest::getTaskId, taskId)
                .eq(DdSupplementRequest::getStatus, SupplementRequestStatus.PENDING.name())
                .count();
    }

    @Override
    public List<String> pendingAgentCodes(Long taskId) {
        return lambdaQuery()
                .select(DdSupplementRequest::getAgentCode)
                .eq(DdSupplementRequest::getTaskId, taskId)
                .eq(DdSupplementRequest::getStatus, SupplementRequestStatus.PENDING.name())
                .list()
                .stream()
                .map(DdSupplementRequest::getAgentCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean existsPending(Long taskId, String dataCode) {
        return lambdaQuery()
                .eq(DdSupplementRequest::getTaskId, taskId)
                .eq(DdSupplementRequest::getDataCode, dataCode)
                .eq(DdSupplementRequest::getStatus, SupplementRequestStatus.PENDING.name())
                .exists();
    }

    private SupplementRequestVO toVO(DdSupplementRequest entity) {
        SupplementRequestVO vo = new SupplementRequestVO();
        vo.setRequestId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setPlanId(entity.getPlanId());
        vo.setExecutionId(entity.getExecutionId());
        vo.setAgentCode(entity.getAgentCode());
        vo.setDataCode(entity.getDataCode());
        vo.setDataName(entity.getDataName());
        vo.setReason(entity.getReason());
        vo.setSuggestedAction(entity.getSuggestedAction());
        vo.setStatus(entity.getStatus());
        vo.setSource(entity.getSource());
        vo.setRemark(entity.getRemark());
        vo.setCompletedAt(entity.getCompletedAt());
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
