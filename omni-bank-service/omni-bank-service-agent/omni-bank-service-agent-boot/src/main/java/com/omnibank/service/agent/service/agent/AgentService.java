package com.omnibank.service.agent.service.agent;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.omnibank.service.agent.dto.agent.AgentCreateReq;
import com.omnibank.service.agent.dto.agent.AgentQueryReq;
import com.omnibank.service.agent.dto.agent.AgentUpdateReq;
import com.omnibank.service.agent.entity.AiAgent;

/**
 * 智能体服务：分页/增删改（MyBatis-Plus IService 体系）
 */
public interface AgentService extends IService<AiAgent> {

    /** 分页查询智能体 */
    IPage<AiAgent> page(AgentQueryReq req);

    /** 按 ID 查询（不存在时抛业务异常） */
    AiAgent getById(Long id);

    /** 按 ID 查询（不存在返回 null，供跨服务契约使用） */
    AiAgent findById(Long id);

    /** 新增智能体 */
    void create(AgentCreateReq req);

    /** 修改智能体 */
    void update(AgentUpdateReq req);

    /** 删除智能体（逻辑删除） */
    void delete(Long id);
}
