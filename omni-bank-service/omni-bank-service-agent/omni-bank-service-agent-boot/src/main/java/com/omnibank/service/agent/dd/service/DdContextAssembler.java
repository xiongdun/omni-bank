package com.omnibank.service.agent.dd.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.entity.DdEvidence;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.mapper.DdEvidenceMapper;
import com.omnibank.service.agent.dd.mapper.DdTaskMapper;
import com.omnibank.service.agent.dd.support.DdViewConverter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 企业上下文组装器：只装配该任务相关的企业与申请信息及证据索引，避免把全量数据塞进上下文
 */
@Component
@RequiredArgsConstructor
public class DdContextAssembler {

    private final DdTaskMapper taskMapper;

    private final DdEnterpriseService enterpriseService;

    private final DdLoanApplicationService applicationService;

    private final DdScenarioService scenarioService;

    private final DdEvidenceMapper evidenceMapper;

    private final DdViewConverter viewConverter;

    public TaskContextVO assemble(Long taskId) {
        DdTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(DdErrorCode.TASK_NOT_FOUND);
        }
        TaskContextVO vo = new TaskContextVO();
        vo.setTaskId(task.getId());
        vo.setEnterpriseId(task.getEnterpriseId());
        vo.setContextVersion(task.getContextVersion());
        vo.setAnalysisVersion(task.getAnalysisVersion());
        vo.setEnterpriseInfo(viewConverter.toEnterpriseVO(enterpriseService.getById(task.getEnterpriseId())));
        vo.setLoanApplication(viewConverter.toApplicationVO(applicationService.requireByTaskId(taskId)));
        vo.setBusinessScenario(scenarioService.latestOrNull(taskId));

        List<DdEvidence> evidences = evidenceMapper.selectList(Wrappers.<DdEvidence>lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .orderByAsc(DdEvidence::getId));
        vo.setEvidenceIds(evidences.stream().map(e -> String.valueOf(e.getId())).toList());
        vo.setCreditCode(vo.getEnterpriseInfo() == null ? null : vo.getEnterpriseInfo().getCreditCode());
        vo.setCustomerId(vo.getEnterpriseInfo() == null ? null : vo.getEnterpriseInfo().getCustomerId());
        return vo;
    }
}
