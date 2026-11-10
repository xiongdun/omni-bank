package com.omnibank.service.agent.dd.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.service.agent.dd.dto.task.TaskContextVO;
import com.omnibank.service.agent.dd.entity.DdEvidence;
import com.omnibank.service.agent.dd.mapper.DdEvidenceMapper;
import com.omnibank.service.agent.dd.service.DdContextAssembler;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 上下文构建器：复用任务上下文装配，只补充 Agent 执行所需的证据引用、工具清单与版本信息
 */
@Component
@RequiredArgsConstructor
public class AgentContextBuilder {

    private static final int EXCERPT_LIMIT = 200;

    private final DdContextAssembler contextAssembler;

    private final DdEvidenceMapper evidenceMapper;

    private final ToolRegistry toolRegistry;

    public AgentContext build(Long taskId) {
        TaskContextVO assembled = contextAssembler.assemble(taskId);
        List<DdEvidence> evidences = evidenceMapper.selectList(Wrappers.<DdEvidence>lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .orderByAsc(DdEvidence::getId));

        AgentContext context = new AgentContext();
        context.setTaskId(taskId);
        context.setEnterpriseId(assembled.getEnterpriseId());
        context.setExecutionId(UUID.randomUUID().toString());
        context.setTraceId(context.getExecutionId());
        if (assembled.getBusinessScenario() != null) {
            context.setBusinessScenario(assembled.getBusinessScenario().getBusinessType());
        }
        context.setEnterpriseProfile(assembled.getEnterpriseInfo());
        context.setCreditApplication(assembled.getLoanApplication());
        context.setEvidences(evidences.stream().map(this::toEvidenceRef).toList());
        context.setAvailableTools(toolRegistry.codes());
        context.setMockMode(evidences.stream().anyMatch(evidence -> Boolean.TRUE.equals(evidence.getIsMock())));

        Map<String, String> versions = new HashMap<>();
        versions.put("contextVersion", String.valueOf(assembled.getContextVersion()));
        versions.put("analysisVersion", String.valueOf(assembled.getAnalysisVersion()));
        context.setInputVersions(versions);
        return context;
    }

    private EvidenceRef toEvidenceRef(DdEvidence evidence) {
        EvidenceRef ref = new EvidenceRef();
        ref.setEvidenceId(String.valueOf(evidence.getId()));
        ref.setEvidenceType(evidence.getEvidenceType());
        ref.setSourceSystem(evidence.getSourceSystem());
        ref.setSourceReference(evidence.getSourceReference());
        ref.setDataPeriod(evidence.getDataPeriod());
        ref.setExcerpt(excerpt(evidence.getContentSummary()));
        return ref;
    }

    private String excerpt(String summary) {
        if (summary == null || summary.length() <= EXCERPT_LIMIT) {
            return summary;
        }
        return summary.substring(0, EXCERPT_LIMIT);
    }
}
