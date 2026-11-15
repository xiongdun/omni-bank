package com.omnibank.service.agent.dd.tool;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tool 业务参数 Schema：集中声明各工具读取的入参名称、类型与是否必填，由 ToolInvoker 调用前统一校验。
 * 未声明的参数一律放行，避免约束 Agent 传入的通用上下文参数（如 enterpriseId）。
 */
public final class ToolParamSchemas {

    public enum ParamType {
        STRING, NUMBER, BOOLEAN, LIST
    }

    public record Param(String name, ParamType type, boolean required) {
    }

    private static final Param CREDIT_CODE_REQUIRED = new Param("creditCode", ParamType.STRING, true);

    private static final Param CREDIT_CODE = new Param("creditCode", ParamType.STRING, false);

    private static final Param ENTERPRISE_NAME = new Param("enterpriseName", ParamType.STRING, false);

    private static final Param ENTERPRISE_ID = new Param("enterpriseId", ParamType.STRING, false);

    private static final Param LATEST_REVENUE = new Param("latestRevenue", ParamType.NUMBER, false);

    private static final Param APPLY_AMOUNT = new Param("applyAmount", ParamType.NUMBER, false);

    private static final Param INDUSTRY_NAME = new Param("industryName", ParamType.STRING, false);

    private static final Param BUSINESS_SCOPE = new Param("businessScope", ParamType.STRING, false);

    private static final Param LOAN_PURPOSE = new Param("loanPurpose", ParamType.STRING, false);

    private static final Param PRODUCT_CODE = new Param("productCode", ParamType.STRING, false);

    private static final Param POLICY_ID_REQUIRED = new Param("policyId", ParamType.STRING, true);

    private static final Param CHAPTERS_REQUIRED = new Param("chapters", ParamType.LIST, true);

    private static final Param EVIDENCE_IDS = new Param("evidenceIds", ParamType.LIST, false);

    private static final Map<String, List<Param>> SCHEMAS = Map.ofEntries(
            Map.entry("enterprise.verifyCreditCode", List.of(CREDIT_CODE)),
            Map.entry("financial.getBankStatements", List.of(CREDIT_CODE_REQUIRED, LATEST_REVENUE, ENTERPRISE_ID)),
            Map.entry("financial.getFinancialStatements", List.of(CREDIT_CODE_REQUIRED, APPLY_AMOUNT, ENTERPRISE_ID)),
            Map.entry("financial.getTaxData", List.of(CREDIT_CODE_REQUIRED, LATEST_REVENUE, ENTERPRISE_ID)),
            Map.entry("credit.getEnterpriseCreditReport",
                    List.of(CREDIT_CODE_REQUIRED, APPLY_AMOUNT,
                            new Param("netAssets", ParamType.NUMBER, false), ENTERPRISE_ID)),
            Map.entry("credit.getDebtMaturity",
                    List.of(CREDIT_CODE_REQUIRED,
                            new Param("usedCredit", ParamType.NUMBER, false), APPLY_AMOUNT, ENTERPRISE_ID)),
            Map.entry("document.listMaterials",
                    List.of(CREDIT_CODE_REQUIRED,
                            new Param("hasGuarantee", ParamType.BOOLEAN, false), ENTERPRISE_ID)),
            Map.entry("document.createEvidence",
                    List.of(new Param("docType", ParamType.STRING, false),
                            new Param("docTypeName", ParamType.STRING, false),
                            new Param("fileName", ParamType.STRING, false),
                            new Param("fileHash", ParamType.STRING, false),
                            new Param("textExcerpt", ParamType.STRING, false),
                            new Param("dataPeriod", ParamType.STRING, false),
                            ENTERPRISE_ID)),
            Map.entry("document.classifyDocument",
                    List.of(new Param("fileName", ParamType.STRING, false),
                            new Param("textExcerpt", ParamType.STRING, false))),
            Map.entry("industry.getIndustryProfile", List.of(INDUSTRY_NAME, BUSINESS_SCOPE, ENTERPRISE_NAME)),
            Map.entry("industry.getIndustryBenchmark", List.of(INDUSTRY_NAME, BUSINESS_SCOPE, ENTERPRISE_NAME)),
            Map.entry("judicial.getAdministrativePenalties",
                    List.of(CREDIT_CODE, ENTERPRISE_NAME, ENTERPRISE_ID)),
            Map.entry("judicial.getEnforcementRecords", List.of(CREDIT_CODE, ENTERPRISE_NAME, ENTERPRISE_ID)),
            Map.entry("judicial.getJudicialRecords", List.of(CREDIT_CODE, ENTERPRISE_NAME, ENTERPRISE_ID)),
            Map.entry("fraud.checkRelatedPartyTransactions", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("fraud.checkIdentityConsistency", List.of(CREDIT_CODE, ENTERPRISE_NAME, ENTERPRISE_ID)),
            Map.entry("fraud.checkFundFlowAnomalies", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("fraud.checkFinancialAnomalies", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("fraud.checkContractAnomalies", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("loanPurpose.classifyLoanPurpose", List.of(LOAN_PURPOSE, PRODUCT_CODE, ENTERPRISE_ID)),
            Map.entry("loanPurpose.calculateWorkingCapitalDemand", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("loanPurpose.calculateProjectFundingDemand", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("loanPurpose.analyzeContract", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("loanPurpose.analyzeFundFlow", List.of(CREDIT_CODE, ENTERPRISE_ID)),
            Map.entry("policy.searchApplicablePolicies",
                    List.of(PRODUCT_CODE, LOAN_PURPOSE, INDUSTRY_NAME, ENTERPRISE_ID)),
            Map.entry("policy.retrievePolicyClauses", List.of(POLICY_ID_REQUIRED, ENTERPRISE_ID)),
            Map.entry("siteVisit.getInvestigationRecords",
                    List.of(CREDIT_CODE, ENTERPRISE_NAME,
                            new Param("registeredAddress", ParamType.STRING, false), ENTERPRISE_ID)),
            Map.entry("siteVisit.getInterviewRecords", List.of(CREDIT_CODE, ENTERPRISE_NAME, ENTERPRISE_ID)),
            Map.entry("report.validateReport", List.of(CHAPTERS_REQUIRED)),
            Map.entry("report.createReportVersion",
                    List.of(CHAPTERS_REQUIRED,
                            new Param("title", ParamType.STRING, false),
                            new Param("reportStatus", ParamType.STRING, false),
                            new Param("reviewStatus", ParamType.STRING, false),
                            EVIDENCE_IDS,
                            new Param("evidenceCount", ParamType.NUMBER, false),
                            new Param("dataGapCount", ParamType.NUMBER, false),
                            new Param("supplementRequestCount", ParamType.NUMBER, false),
                            new Param("exportStatus", ParamType.STRING, false),
                            new Param("modelVersion", ParamType.STRING, false),
                            new Param("promptVersion", ParamType.STRING, false))));

    private ToolParamSchemas() {
    }

    /** 返回校验失败原因；null 表示通过。未登记 Schema 的工具直接放行 */
    public static String validate(String toolCode, Map<String, Object> params) {
        List<Param> schema = SCHEMAS.get(toolCode);
        if (schema == null) {
            return null;
        }
        Map<String, Object> values = params == null ? Map.of() : params;
        for (Param param : schema) {
            Object value = values.get(param.name());
            if (isEmpty(value)) {
                if (param.required()) {
                    return "缺少必填参数：" + param.name();
                }
                continue;
            }
            if (!matches(param.type(), value)) {
                return "参数 " + param.name() + " 类型不合法，期望 " + param.type();
            }
        }
        return null;
    }

    private static boolean isEmpty(Object value) {
        return value == null || (value instanceof CharSequence text && text.toString().isBlank());
    }

    private static boolean matches(ParamType type, Object value) {
        return switch (type) {
            // 字符串参数内部统一按 String.valueOf 使用，仅拒绝集合与对象等明显不合法类型
            case STRING -> !(value instanceof Map<?, ?> || value instanceof Iterable<?>);
            case NUMBER -> value instanceof Number || isNumber(value.toString());
            case BOOLEAN -> value instanceof Boolean || "true".equalsIgnoreCase(value.toString())
                    || "false".equalsIgnoreCase(value.toString());
            case LIST -> value instanceof List<?>;
        };
    }

    private static boolean isNumber(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
