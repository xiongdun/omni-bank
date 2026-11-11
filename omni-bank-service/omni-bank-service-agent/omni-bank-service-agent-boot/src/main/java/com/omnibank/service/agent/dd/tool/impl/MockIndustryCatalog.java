package com.omnibank.service.agent.dd.tool.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Mock 行业知识目录：按行业名称、经营范围、企业名称关键字匹配国标行业，数据仅用于链路验证。
 * 未匹配时返回 UNKNOWN，由 Agent 记数据缺口，不抛异常、不臆造行业数据。
 */
final class MockIndustryCatalog {

    static final String UNKNOWN_CODE = "UNKNOWN";

    static final String UNKNOWN_NAME = "未知行业";

    private static final String DATA_PERIOD = "2024年度";

    private static final String SCOPE_NOTE = "Mock 行业研究，全国口径，2024 年度";

    private static final IndustryProfileSnapshot UNKNOWN_PROFILE = new IndustryProfileSnapshot(UNKNOWN_CODE,
            UNKNOWN_NAME, null, List.of(), "UNKNOWN", null, "UNKNOWN",
            List.of("行业未识别，无法评估行业环境、周期与竞争格局"), "—",
            "未命中 Mock 行业知识库，需人工补充行业归属");

    private static final IndustryBenchmarkSnapshot UNKNOWN_BENCHMARK = new IndustryBenchmarkSnapshot(UNKNOWN_CODE,
            UNKNOWN_NAME, null, null, null, null, "—", "行业未识别，无可用对标基准");

    private static final List<CatalogEntry> ENTRIES = List.of(
            entry("I65", "软件和信息技术服务业", List.of("软件", "信息技术", "科技", "互联网"),
                    "120000", List.of("8.50", "9.20", "10.10"), "鼓励", "35.50", "成长期",
                    List.of("技术迭代快，研发投入压力大", "客户集中度较高，续约存在不确定性", "人力成本持续上升"),
                    "42.50", "48.00", 95, "9.50", "样本 120 家，全国口径，2024 年度"),
            entry("C36", "汽车制造业", List.of("汽车", "整车", "零部件"),
                    "98000", List.of("3.20", "5.10", "4.60"), "鼓励", "68.00", "成熟期",
                    List.of("价格竞争加剧，整车厂持续压价", "钢材与芯片等原材料价格波动", "新能源转型资本开支大"),
                    "18.50", "62.00", 72, "4.80", "样本 150 家，全国口径，2024 年度"),
            entry("C27", "医药制造业", List.of("医药", "制药", "生物"),
                    "35000", List.of("6.10", "5.50", "5.80"), "鼓励", "45.00", "成长期",
                    List.of("研发周期长、投入高", "集采与医保控费带来降价压力", "质量与合规监管趋严"),
                    "55.00", "40.00", 110, "5.60", "样本 90 家，全国口径，2024 年度"),
            entry("C34", "通用设备制造业", List.of("机械", "设备", "装备", "制造"),
                    "150000", List.of("3.80", "4.20", "3.50"), "鼓励", "32.00", "成熟期",
                    List.of("下游资本开支波动", "钢材等原材料价格波动", "应收账款周转周期拉长"),
                    "22.00", "55.00", 100, "3.80", "样本 180 家，全国口径，2024 年度"),
            entry("C14", "食品制造业", List.of("食品", "饮料", "农产品加工"),
                    "90000", List.of("4.50", "5.00", "4.20"), "鼓励", "38.00", "成熟期",
                    List.of("食品安全事件影响大", "农产品原料价格波动", "渠道与营销费用上升"),
                    "30.00", "45.00", 60, "4.50", "样本 130 家，全国口径，2024 年度"),
            entry("F51", "批发业", List.of("批发", "贸易", "商贸", "供应链"),
                    "500000", List.of("2.50", "1.80", "1.20"), "中性", "22.00", "成熟期",
                    List.of("毛利率低，议价能力弱", "大宗商品价格波动", "资金占用与垫资压力大"),
                    "8.50", "68.00", 45, "2.00", "样本 200 家，全国口径，2024 年度"),
            entry("E48", "土木工程建筑业", List.of("建筑", "工程", "建设", "施工"),
                    "310000", List.of("4.00", "2.20", "-1.50"), "中性", "28.00", "成熟期",
                    List.of("应收账款回收慢", "项目垫资压力大", "地产链景气度拖累"),
                    "12.00", "75.00", 180, "1.00", "样本 160 家，全国口径，2024 年度"),
            entry("K70", "房地产业", List.of("房地产", "地产", "置业"),
                    "180000", List.of("-2.00", "-6.50", "-8.00"), "限制", "55.00", "衰退期",
                    List.of("销售去化压力大，现金流紧张", "融资渠道收窄", "土地与存货减值风险"),
                    "20.00", "80.00", 300, "-5.00", "样本 140 家，全国口径，2024 年度"));

    private MockIndustryCatalog() {
    }

    static IndustryProfileSnapshot resolveProfile(String industryName, String businessScope, String enterpriseName) {
        return match(industryName, businessScope, enterpriseName)
                .map(CatalogEntry::profile).orElse(UNKNOWN_PROFILE);
    }

    static IndustryBenchmarkSnapshot resolveBenchmark(String industryName, String businessScope,
                                                      String enterpriseName) {
        return match(industryName, businessScope, enterpriseName)
                .map(CatalogEntry::benchmark).orElse(UNKNOWN_BENCHMARK);
    }

    private static Optional<CatalogEntry> match(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (CatalogEntry entry : ENTRIES) {
                for (String keyword : entry.keywords()) {
                    if (text.contains(keyword)) {
                        return Optional.of(entry);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static CatalogEntry entry(String code, String name, List<String> keywords, String marketSize,
                                      List<String> growthRates, String policy, String cr5, String cycleStage,
                                      List<String> risks, String grossMargin, String debtRatio, int turnoverDays,
                                      String revenueGrowth, String sampleNote) {
        IndustryProfileSnapshot profile = new IndustryProfileSnapshot(code, name, decimal(marketSize),
                growthRates.stream().map(BigDecimal::new).toList(), policy, decimal(cr5), cycleStage, risks,
                DATA_PERIOD, SCOPE_NOTE);
        IndustryBenchmarkSnapshot benchmark = new IndustryBenchmarkSnapshot(code, name, decimal(grossMargin),
                decimal(debtRatio), turnoverDays, decimal(revenueGrowth), DATA_PERIOD, sampleNote);
        return new CatalogEntry(keywords, profile, benchmark);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record CatalogEntry(List<String> keywords, IndustryProfileSnapshot profile,
                                IndustryBenchmarkSnapshot benchmark) {
    }
}
