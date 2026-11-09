package com.omnibank.service.agent.api.dto;

import com.omnibank.common.core.dto.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 智能体配置 DTO（跨服务传输）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentDTO extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 智能体 ID */
    private Long id;

    /** 智能体编码（唯一） */
    private String agentCode;

    /** 智能体名称 */
    private String agentName;

    /** 模型名称（如 qwen-max） */
    private String modelName;

    /** 系统提示词 */
    private String systemPrompt;

    /** 采样温度 */
    private Double temperature;

    /** 单次回复最大 token 数 */
    private Integer maxTokens;

    /** 状态：0 启用 1 停用 */
    private String status;
}
