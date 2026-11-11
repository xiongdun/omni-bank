package com.omnibank.service.agent.dd.tool.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 本地 Mock 制度库：第一期只做条款检索，不接真实向量库；条款原文、页码、版本与审批权限均按 Mock 制度登记，
 * 用于验证“可回溯原文”的链路，不能作为真实合规依据。
 */
public final class MockPolicyCatalog {

    private static final PolicyDoc WORKING_CAPITAL = new PolicyDoc("POL-WC-2025", "流动资金贷款管理办法", "2025.1",
            LocalDate.of(2025, 1, 1), null, "现行有效",
            "mock_policies/流动资金贷款管理办法_2025.1.pdf", "MOCK-SHA256-WC-2025-1",
            List.of("CORPORATE_WORKING_CAPITAL"), List.of("原材料", "采购", "周转", "经营", "贸易"), List.of("*"),
            List.of(new ClauseTemplate("第三章", "第九条", "P12",
                            "流动资金贷款仅用于借款人日常生产经营周转，不得用于固定资产投资、股权投资、房地产及国家限制行业。",
                            null),
                    new ClauseTemplate("第四章", "第十五条", "P18",
                            "单笔支付金额超过 1000 万元人民币的，应采用受托支付方式。",
                            "超过 5000 万元的流动资金贷款需报总行审批（Mock 制度示例，需人工核对现行版本）")));

    private static final PolicyDoc FIXED_ASSET = new PolicyDoc("POL-FA-2024", "固定资产贷款管理办法", "2024.2",
            LocalDate.of(2024, 6, 1), null, "现行有效",
            "mock_policies/固定资产贷款管理办法_2024.2.pdf", "MOCK-SHA256-FA-2024-2",
            List.of("CORPORATE_FIXED_ASSET"), List.of("项目建设", "项目", "建设", "工程", "设备", "购置"), List.of("*"),
            List.of(new ClauseTemplate("第二章", "第七条", "P8",
                            "固定资产贷款用于借款人新建、扩建、改造、购置固定资产等，贷款期限一般不超过 10 年。",
                            null),
                    new ClauseTemplate("第三章", "第十二条", "P14",
                            "项目资本金比例应符合国家有关规定，原则上不低于项目总投资的 20%。",
                            "项目资本金比例与例外情形需按项目所属行业规定执行（Mock 制度示例，需人工核对现行版本）")));

    private static final PolicyDoc ADMISSION = new PolicyDoc("POL-ADM-2026", "对公客户准入管理办法", "2026.1",
            LocalDate.of(2026, 1, 1), null, "现行有效",
            "mock_policies/对公客户准入管理办法_2026.1.pdf", "MOCK-SHA256-ADM-2026-1",
            List.of("*"), List.of("*"), List.of("*"),
            List.of(new ClauseTemplate("第二章", "第五条", "P6",
                            "客户准入需满足依法设立、持续经营、无重大不良信用记录等基本条件。",
                            null),
                    new ClauseTemplate("第三章", "第十一条", "P11",
                            "关注类客户新增授信需提升一级审批权限。",
                            "关注类客户新增授信需提升一级审批权限（Mock 制度示例，需人工核对现行授权文件）")));

    private static final List<PolicyDoc> POLICIES = List.of(WORKING_CAPITAL, FIXED_ASSET, ADMISSION);

    private MockPolicyCatalog() {
    }

    public static List<PolicyDoc> all() {
        return POLICIES;
    }

    public static Optional<PolicyDoc> find(String policyId) {
        if (policyId == null) {
            return Optional.empty();
        }
        return POLICIES.stream().filter(policy -> policy.policyId().equals(policyId)).findFirst();
    }

    /** 制度元数据 */
    public record PolicyDoc(String policyId, String policyName, String policyVersion, LocalDate effectiveDate,
                            LocalDate expiryDate, String status, String sourceFile, String fileHash,
                            List<String> applicableProducts, List<String> applicablePurposes,
                            List<String> industries, List<ClauseTemplate> clauses) {
    }

    /** 条款模板：原文与页码必须固定可回溯 */
    public record ClauseTemplate(String sectionNo, String clauseNo, String pageNo, String originalText,
                                 String approvalAuthorityNote) {
    }
}
