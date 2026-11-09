package com.omnibank.service.agent.dd.dto.enterprise;

import com.omnibank.common.core.vo.BaseVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业主体视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseVO extends BaseVO {

    private Long id;

    /** 银行企业客户编号 */
    private String customerId;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 企业名称 */
    private String enterpriseName;

    /** 法定代表人 */
    private String legalPerson;

    /** 注册地址 */
    private String registeredAddress;

    /** 经营范围 */
    private String businessScope;

    /** 注册资本 */
    private BigDecimal registeredCapital;

    /** 成立日期 */
    private LocalDate establishmentDate;

    /** 经营状态 */
    private String businessStatus;

    /** 行业编码 */
    private String industryCode;

    /** 行业名称 */
    private String industryName;

    /** 数据来源 */
    private String dataSource;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;
}
