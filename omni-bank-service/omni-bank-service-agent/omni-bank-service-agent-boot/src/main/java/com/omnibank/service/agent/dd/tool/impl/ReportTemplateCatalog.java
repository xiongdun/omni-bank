package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.plan.DdAgentCatalog;
import java.util.List;
import java.util.Optional;

/**
 * 尽调报告模板目录：固定 18 章，声明必备章节与证据要求，P7 不做模板配置化
 */
final class ReportTemplateCatalog {

    static final String TEMPLATE_VERSION = "REPORT_TEMPLATE_V1";

    private ReportTemplateCatalog() {
    }

    static List<ReportSnapshots.ChapterTemplate> chapters() {
        return List.of(
                chapter(1, "CREDIT_APPLICATION", "授信申请基本情况", true, false, List.of()),
                chapter(2, "ENTERPRISE_PROFILE", "企业主体信息", true, true,
                        List.of(DdAgentCatalog.SUBJECT_VERIFICATION)),
                chapter(3, "EQUITY_RELATIONS", "股权结构及关联关系", false, false,
                        List.of(DdAgentCatalog.FRAUD_DETECTION)),
                chapter(4, "BUSINESS_OPERATION", "企业经营情况", false, false,
                        List.of(DdAgentCatalog.INDUSTRY_EXPERT, DdAgentCatalog.SITE_VISIT)),
                chapter(5, "INDUSTRY_ANALYSIS", "行业分析", false, false,
                        List.of(DdAgentCatalog.INDUSTRY_EXPERT)),
                chapter(6, "FINANCIAL_ANALYSIS", "财务分析及偿债能力", true, true,
                        List.of(DdAgentCatalog.FINANCIAL_ANALYSIS)),
                chapter(7, "CREDIT_DEBT", "征信及债务情况", true, true,
                        List.of(DdAgentCatalog.CREDIT_DEBT_ANALYSIS)),
                chapter(8, "JUDICIAL_RISK", "司法风险分析", false, false,
                        List.of(DdAgentCatalog.JUDICIAL_RISK)),
                chapter(9, "SITE_VISIT", "现场调查情况", false, false,
                        List.of(DdAgentCatalog.SITE_VISIT)),
                chapter(10, "MATERIAL_COMPLETENESS", "尽调材料完整性", false, false,
                        List.of(DdAgentCatalog.DOCUMENT_RECOGNITION)),
                chapter(11, "LOAN_PURPOSE", "贷款用途及资金需求分析", true, true,
                        List.of(DdAgentCatalog.LOAN_PURPOSE_ANALYSIS)),
                chapter(12, "FRAUD_ANALYSIS", "反欺诈分析", false, false,
                        List.of(DdAgentCatalog.FRAUD_DETECTION)),
                chapter(13, "POLICY_MATCHING", "审批政策匹配", false, false,
                        List.of(DdAgentCatalog.POLICY_MATCHING)),
                chapter(14, "COMPREHENSIVE_RISK", "综合风险评估", true, false,
                        List.of(DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT)),
                chapter(15, "AMOUNT_TERM", "金额及期限参考意见", false, false,
                        List.of(DdAgentCatalog.LOAN_PURPOSE_ANALYSIS,
                                DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT)),
                chapter(16, "RISK_MITIGATION", "风险缓释措施", false, false,
                        List.of(DdAgentCatalog.COMPREHENSIVE_RISK_ASSESSMENT)),
                chapter(17, "SUPPLEMENT_ITEMS", "补充材料及调查事项", false, false, List.of()),
                chapter(18, "REVIEW_OPINION", "客户经理复核意见", false, false, List.of()));
    }

    static Optional<ReportSnapshots.ChapterTemplate> find(String chapterCode) {
        if (chapterCode == null) {
            return Optional.empty();
        }
        return chapters().stream()
                .filter(chapter -> chapter.chapterCode().equals(chapterCode))
                .findFirst();
    }

    private static ReportSnapshots.ChapterTemplate chapter(int chapterNo, String chapterCode, String title,
                                                           boolean required, boolean requireEvidence,
                                                           List<String> sourceAgents) {
        return new ReportSnapshots.ChapterTemplate(chapterNo, chapterCode, title, required, requireEvidence,
                sourceAgents);
    }
}
