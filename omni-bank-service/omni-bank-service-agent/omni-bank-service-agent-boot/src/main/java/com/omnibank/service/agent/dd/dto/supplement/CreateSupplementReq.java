package com.omnibank.service.agent.dd.dto.supplement;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 手工创建补充材料请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateSupplementReq extends BaseDTO {

    /** 数据编码 */
    @NotBlank(message = "数据编码不能为空")
    @Size(max = 64, message = "数据编码长度不能超过 64")
    private String dataCode;

    /** 数据名称 */
    @Size(max = 256, message = "数据名称长度不能超过 256")
    private String dataName;

    /** 请求原因 */
    @Size(max = 1000, message = "请求原因长度不能超过 1000")
    private String reason;

    /** 建议补充方式 */
    @Size(max = 1000, message = "建议补充方式长度不能超过 1000")
    private String suggestedAction;

    /** 关联 Agent 编码，为空时完成后按全量重分析处理 */
    @Size(max = 64, message = "Agent 编码长度不能超过 64")
    private String agentCode;
}
