package com.omnibank.service.agent.dd.ai.agent;

import com.omnibank.service.agent.dd.tool.impl.AdministrativePenaltiesSnapshot;
import com.omnibank.service.agent.dd.tool.impl.EnforcementRecordsSnapshot;
import com.omnibank.service.agent.dd.tool.impl.JudicialRecordsSnapshot;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 司法风险分析输出：涉诉与执行结构、金额合计、占申请金额比例及主体匹配结论
 */
@Data
public class JudicialRiskData {

    private String enterpriseName;

    private String creditCode;

    /** 司法记录主体名称是否与申报主体精确匹配 */
    private Boolean subjectMatchExact;

    /** 主体匹配方式：EXACT / FUZZY */
    private String subjectMatchMode;

    /** 外部数据命中的主体名称 */
    private String matchedSubjectName;

    private Integer lawsuitCount;

    private Integer plaintiffCount;

    private Integer defendantCount;

    private Integer thirdPartyCount;

    /** 被告未结案件数量 */
    private Integer pendingDefendantCaseCount;

    /** 被告未结案件金额合计 */
    private BigDecimal pendingDefendantAmount;

    /** 被执行人金额合计 */
    private BigDecimal enforcementAmount;

    /** 行政处罚金额合计 */
    private BigDecimal penaltyAmount;

    /** 授信申请金额 */
    private BigDecimal applyAmount;

    /** 被告未结金额占申请金额比例（百分比，保留 2 位） */
    private BigDecimal pendingDefendantAmountRatio;

    /** 被执行金额占申请金额比例（百分比，保留 2 位） */
    private BigDecimal enforcementAmountRatio;

    /** 处罚金额占申请金额比例（百分比，保留 2 位） */
    private BigDecimal penaltyAmountRatio;

    private List<JudicialRecordsSnapshot.JudicialCase> lawsuits;

    private List<EnforcementRecordsSnapshot.EnforcementRecord> enforcements;

    private List<AdministrativePenaltiesSnapshot.AdministrativePenaltyRecord> penalties;

    /** 可引用的证据数量 */
    private Integer evidenceCount;
}
