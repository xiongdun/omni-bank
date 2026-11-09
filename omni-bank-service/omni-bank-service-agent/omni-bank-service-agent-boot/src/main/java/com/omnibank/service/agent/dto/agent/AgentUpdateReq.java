package com.omnibank.service.agent.dto.agent;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 智能体修改请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentUpdateReq extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 智能体 ID */
    @NotNull(message = "智能体 ID 不能为空")
    private Long id;

    /** 智能体名称 */
    private String agentName;

    /** 描述 */
    private String description;

    /** 模型名称 */
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
