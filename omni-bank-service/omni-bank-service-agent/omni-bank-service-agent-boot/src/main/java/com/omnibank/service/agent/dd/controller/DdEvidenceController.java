package com.omnibank.service.agent.dd.controller;

import com.omnibank.common.core.result.Result;
import com.omnibank.service.agent.dd.dto.evidence.EvidenceDetailVO;
import com.omnibank.service.agent.dd.service.DdEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 证据接口：服务内路径 /v1/dd/evidences，经网关 StripPrefix 后对外为 /api/agent/v1/dd/evidences
 */
@RestController
@RequestMapping("/v1/dd/evidences")
@RequiredArgsConstructor
public class DdEvidenceController {

    private final DdEvidenceService evidenceService;

    /** 证据详情 */
    @GetMapping("/{evidenceId}")
    public Result<EvidenceDetailVO> detail(@PathVariable Long evidenceId) {
        return Result.success(evidenceService.detail(evidenceId));
    }
}
