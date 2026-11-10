package com.omnibank.service.agent.dd.ai.agent;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 主体信息核验输出：主体标准信息、一致性结论与证据完备度
 */
@Data
public class SubjectVerificationData {

    private String enterpriseName;

    private String creditCode;

    private String customerId;

    private String legalPerson;

    private String businessStatus;

    /** 统一社会信用代码是否通过校验 */
    private Boolean creditCodeValid;

    /** 校验失败原因 */
    private String creditCodeInvalidReason;

    /** 申报名称与登记名称是否一致 */
    private Boolean nameConsistent;

    /** 申报代码与登记代码是否一致 */
    private Boolean creditCodeConsistent;

    /** 是否取得工商登记证据 */
    private Boolean registryEvidenceFound;

    /** 可引用的证据数量 */
    private Integer evidenceCount;

    /** 必需主体字段完备度（0-1） */
    private BigDecimal evidenceCompleteness;
}
