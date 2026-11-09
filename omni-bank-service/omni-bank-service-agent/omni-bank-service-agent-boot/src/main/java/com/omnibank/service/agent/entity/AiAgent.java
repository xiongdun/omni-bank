package com.omnibank.service.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent")
public class AiAgent extends BaseEntity {

    /** 智能体编码（唯一） */
    private String agentCode;

    /** 智能体名称 */
    private String agentName;

    /** 描述 */
    private String description;

    /** 模型名称（如 qwen-max） */
    private String modelName;

    /** 系统提示词 */
    private String systemPrompt;

    /** 采样温度（0-2） */
    private Double temperature;

    /** 单次回复最大 token 数 */
    private Integer maxTokens;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 备注 */
    private String remark;
}
