package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对公信贷业务类型：仅作识别结果分类，具体认定口径待银行业务部门确认
 */
@Getter
@AllArgsConstructor
public enum BusinessType {

    WORKING_CAPITAL("流动资金贷款"),
    FIXED_ASSET("固定资产贷款"),
    TRADE_FINANCE("贸易融资"),
    PROJECT_FINANCE("项目融资"),
    SUPPLY_CHAIN("供应链融资"),
    COMPREHENSIVE_CREDIT("综合授信"),
    OTHER("其他待确认场景");

    private final String desc;
}
