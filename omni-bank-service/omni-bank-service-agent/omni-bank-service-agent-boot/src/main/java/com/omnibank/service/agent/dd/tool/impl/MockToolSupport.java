package com.omnibank.service.agent.dd.tool.impl;

import java.util.Locale;

/**
 * Mock 工具公共派生逻辑：以主体标识为种子生成确定性数据，避免随机数导致同一主体结果漂移
 */
final class MockToolSupport {

    private MockToolSupport() {
    }

    /** 由统一社会信用代码派生稳定种子，相同主体每次执行结果一致 */
    static int seed(String creditCode) {
        return Math.floorMod(normalizedCode(creditCode).hashCode(), 10_000);
    }

    static String normalizedCode(String creditCode) {
        return creditCode == null || creditCode.isBlank()
                ? "MOCK-DEFAULT" : creditCode.trim().toUpperCase(Locale.ROOT);
    }

    /** 主体名称是否只能模糊匹配：命中时上层必须标记待核实，不得直接形成风险结论 */
    static boolean fuzzyMatch(String creditCode) {
        return seed(creditCode) % 7 == 0;
    }

    static String matchedSubject(String enterpriseName, String creditCode) {
        String name = enterpriseName == null || enterpriseName.isBlank()
                ? "未提供企业名称" : enterpriseName.trim();
        return fuzzyMatch(creditCode) ? name + "（名称近似主体）" : name;
    }

    static String matchMode(String creditCode) {
        return fuzzyMatch(creditCode) ? "FUZZY" : "EXACT";
    }

    static Long enterpriseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
