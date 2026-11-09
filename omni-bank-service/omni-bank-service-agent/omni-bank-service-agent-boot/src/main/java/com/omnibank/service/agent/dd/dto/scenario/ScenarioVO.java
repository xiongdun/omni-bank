package com.omnibank.service.agent.dd.dto.scenario;

import com.omnibank.common.core.vo.BaseVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务场景识别结果视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScenarioVO extends BaseVO {

    private Long id;

    /** 识别版本 */
    private Integer scenarioVersion;

    /** 业务类型 */
    private String businessType;

    /** 业务类型中文名 */
    private String businessTypeDesc;

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

    /** 担保方式集合 */
    private List<String> guaranteeTypes;

    /** 识别置信度 */
    private BigDecimal confidence;

    /** 识别状态 */
    private String identifyStatus;

    /** 识别明细 */
    private Map<String, Object> scenarioDetail;

    /** 证据ID集合 */
    private List<String> evidenceIds;

    /** 待确认事项 */
    private List<String> uncertainItems;

    /** 模型名称（规则识别时为空） */
    private String modelName;

    /** Prompt 版本 */
    private String promptVersion;

    /** 识别时间 */
    private LocalDateTime createTime;
}
