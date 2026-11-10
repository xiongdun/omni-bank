package com.omnibank.service.agent.dd.tool.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceDetailVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceListVO;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceQueryReq;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 工商登记信息查询工具（Mock）：返回证据中心已采集的工商证据。
 * 真实工商接口未确认前不直连外部系统，Mock 标记随定义与结果一并返回。
 */
@Component
@RequiredArgsConstructor
public class MockEnterpriseRegistryTool implements AgentTool<EnterpriseRegistrySnapshot> {

    public static final String TOOL_CODE = "enterprise.queryEnterprise";

    private final DdEvidenceService evidenceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "工商登记信息查询（Mock）",
                "返回证据中心已采集的工商登记证据，真实工商接口未确认前使用 Mock 数据", "QUERY", true, 3000);
    }

    @Override
    public ToolResult<EnterpriseRegistrySnapshot> execute(ToolRequest request) {
        if (request.taskId() == null) {
            return ToolResult.failure(TOOL_CODE, null, "taskId 不能为空");
        }
        EvidenceQueryReq query = new EvidenceQueryReq();
        query.setCurrent(1);
        query.setSize(1);
        query.setEvidenceType(EvidenceType.ENTERPRISE_PROFILE.name());
        IPage<EvidenceListVO> page = evidenceService.page(request.taskId(), query);
        if (page.getRecords().isEmpty()) {
            return ToolResult.notFound(TOOL_CODE, "未找到工商登记证据");
        }
        EvidenceDetailVO detail = evidenceService.detail(page.getRecords().get(0).getEvidenceId());
        EnterpriseRegistrySnapshot snapshot = new EnterpriseRegistrySnapshot(detail.getEvidenceId(),
                detail.getContentSummary(), detail.getSourceSystem(), detail.getSourceReference(),
                detail.getDataPeriod(), Boolean.TRUE.equals(detail.getIsMock()));
        return ToolResult.success(detail.getSourceSystem(), snapshot,
                List.of(String.valueOf(detail.getEvidenceId())),
                Map.of("mock", Boolean.TRUE.equals(detail.getIsMock()), "evidenceType", detail.getEvidenceType()));
    }
}
