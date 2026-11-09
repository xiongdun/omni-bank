package com.omnibank.service.agent.controller.agent;

import com.omnibank.common.core.page.PageResult;
import com.omnibank.common.core.result.Result;
import com.omnibank.common.mybatis.util.PageResults;
import com.omnibank.service.agent.dto.agent.AgentCreateReq;
import com.omnibank.service.agent.dto.agent.AgentQueryReq;
import com.omnibank.service.agent.dto.agent.AgentUpdateReq;
import com.omnibank.service.agent.entity.AiAgent;
import com.omnibank.service.agent.service.agent.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体管理接口
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** 分页查询智能体 */
    @GetMapping("/page")
    public Result<PageResult<AiAgent>> page(AgentQueryReq req) {
        return Result.success(PageResults.from(agentService.page(req)));
    }

    /** 智能体详情 */
    @GetMapping("/{id}")
    public Result<AiAgent> get(@PathVariable Long id) {
        return Result.success(agentService.getById(id));
    }

    /** 新增智能体 */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AgentCreateReq req) {
        agentService.create(req);
        return Result.success();
    }

    /** 修改智能体 */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AgentUpdateReq req) {
        agentService.update(req);
        return Result.success();
    }

    /** 删除智能体（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.success();
    }
}
