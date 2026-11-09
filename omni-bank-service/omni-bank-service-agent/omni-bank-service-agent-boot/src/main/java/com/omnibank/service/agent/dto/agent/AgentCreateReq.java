package com.omnibank.service.agent.dto.agent;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 智能体新增请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentCreateReq extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 智能体编码 */
    @NotBlank(message = "智能体编码不能为空")
    private String agentCode;

    /** 智能体名称 */
    @NotBlank(message = "智能体名称不能为空")
    private String agentName;

    /** 描述 */
    private String description;

    /** 模型名称 */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /** 系统提示词 */
    private String systemPrompt;

    /** 采样温度（0-2） */
    private Double temperature = 0.7;

    /** 单次回复最大 token 数 */
    private Integer maxTokens = 2048;

    /** 状态（0 正常 1 停用） */
    private String status = "0";

    /** 备注 */
    private String remark;
}
