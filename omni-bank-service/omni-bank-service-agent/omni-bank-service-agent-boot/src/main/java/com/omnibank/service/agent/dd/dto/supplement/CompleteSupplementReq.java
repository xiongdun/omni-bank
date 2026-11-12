package com.omnibank.service.agent.dd.dto.supplement;

import com.omnibank.common.core.dto.BaseDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 完成补充材料请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompleteSupplementReq extends BaseDTO {

    /** 完成说明 */
    @Size(max = 1000, message = "完成说明长度不能超过 1000")
    private String remark;
}
