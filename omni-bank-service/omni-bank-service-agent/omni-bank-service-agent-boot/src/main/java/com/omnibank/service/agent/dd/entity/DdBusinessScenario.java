package com.omnibank.service.agent.dd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnibank.common.mybatis.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务场景识别结果：按 scenarioVersion 版本化保存，历史版本不覆盖
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dd_business_scenario")
public class DdBusinessScenario extends BaseEntity {

    /** 任务ID */
    private Long taskId;

    /** 识别版本 */
    private Integer scenarioVersion;

    /** 业务类型 */
    private String businessType;

    /** 产品类型 */
    private String productType;

    /** 行业编码 */
    private String industryCode;

    /** 行业名称 */
    private String industryName;

    /** 贷款用途类型 */
    private String loanPurposeType;

    /** 融资模式 */
    private String financingMode;

    /** 担保方式集合（JSON） */
    private String guaranteeTypes;

    /** 识别置信度 */
    private BigDecimal confidence;

    /** 识别状态，见 ScenarioIdentifyStatus */
    private String identifyStatus;

    /** 识别明细（JSON） */
    private String scenarioDetail;

    /** 证据ID集合（JSON） */
    private String evidenceIds;

    /** 待确认事项（JSON） */
    private String uncertainItems;

    /** 模型名称（规则识别时为空） */
    private String modelName;

    /** Prompt 版本 */
    private String promptVersion;

    /** 乐观锁版本 */
    private Integer version;
}
