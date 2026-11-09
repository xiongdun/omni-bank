package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 授信申请信息：一个尽调任务只对应一条申请记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_loan_application")
public class DdLoanApplication extends BaseEntity {

    /** 任务ID（唯一） */
    private Long taskId;

    /** 原信贷系统申请编号 */
    private String applicationNo;

    /** 原始申请业务类型 */
    private String applicationType;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 申请金额 */
    private BigDecimal applyAmount;

    /** 币种 */
    private String currency;

    /** 申请期限 */
    private Integer applyTerm;

    /** 期限单位 */
    private String termUnit;

    /** 贷款用途 */
    private String loanPurpose;

    /** 还款方式 */
    private String repaymentMethod;

    /** 担保方式 */
    private String guaranteeType;

    /** 原始申请快照（JSON） */
    private String applicationSnapshot;

    /** 乐观锁版本 */
    private Integer version;
}
