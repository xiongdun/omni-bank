package com.omnibank.service.agent.dd.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 尽调 Agent 目录：登记 12 个专业 Agent 及其初始依赖，来源为《Agent 开发规格文档》§5.1。
 * 计划校验、规则兜底与 Graph 构建统一使用本目录，避免在多处重复硬编码 Agent 编码。
 */
public final class DdAgentCatalog {

    /** Agent 条目：编码、名称与直接上游依赖（仅 Agent 之间，Planner 与授信申请为外部输入） */
    public record AgentSpec(String agentCode, String agentName, List<String> dependsOn) {

        public AgentSpec {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }

    public static final String SUBJECT_VERIFICATION = "SUBJECT_VERIFICATION";

    public static final String FINANCIAL_ANALYSIS = "FINANCIAL_ANALYSIS";

    public static final String JUDICIAL_RISK = "JUDICIAL_RISK";

    public static final String CREDIT_DEBT_ANALYSIS = "CREDIT_DEBT_ANALYSIS";

    public static final String SITE_VISIT = "SITE_VISIT";

    public static final String DOCUMENT_RECOGNITION = "DOCUMENT_RECOGNITION";

    public static final String INDUSTRY_EXPERT = "INDUSTRY_EXPERT";

    public static final String LOAN_PURPOSE_ANALYSIS = "LOAN_PURPOSE_ANALYSIS";

    public static final String FRAUD_DETECTION = "FRAUD_DETECTION";

    public static final String POLICY_MATCHING = "POLICY_MATCHING";

    public static final String COMPREHENSIVE_RISK_ASSESSMENT = "COMPREHENSIVE_RISK_ASSESSMENT";

    public static final String DUE_DILIGENCE_REPORT = "DUE_DILIGENCE_REPORT";

    private static final List<AgentSpec> SPECS = List.of(
            new AgentSpec(SUBJECT_VERIFICATION, "主体信息核验 Agent", List.of()),
            new AgentSpec(FINANCIAL_ANALYSIS, "财务分析 Agent", List.of(SUBJECT_VERIFICATION, DOCUMENT_RECOGNITION)),
            new AgentSpec(JUDICIAL_RISK, "司法风险 Agent", List.of(SUBJECT_VERIFICATION)),
            new AgentSpec(CREDIT_DEBT_ANALYSIS, "征信债务 Agent", List.of(SUBJECT_VERIFICATION)),
            new AgentSpec(SITE_VISIT, "现场调查 Agent", List.of(DOCUMENT_RECOGNITION, SUBJECT_VERIFICATION)),
            new AgentSpec(DOCUMENT_RECOGNITION, "材料识别 Agent", List.of()),
            new AgentSpec(INDUSTRY_EXPERT, "行业专家 Agent", List.of(SUBJECT_VERIFICATION, FINANCIAL_ANALYSIS)),
            new AgentSpec(LOAN_PURPOSE_ANALYSIS, "贷款用途 Agent",
                    List.of(FINANCIAL_ANALYSIS, INDUSTRY_EXPERT)),
            new AgentSpec(FRAUD_DETECTION, "反欺诈 Agent", List.of(SUBJECT_VERIFICATION, FINANCIAL_ANALYSIS,
                    JUDICIAL_RISK, CREDIT_DEBT_ANALYSIS, SITE_VISIT, DOCUMENT_RECOGNITION, LOAN_PURPOSE_ANALYSIS)),
            new AgentSpec(POLICY_MATCHING, "政策匹配 Agent", List.of(LOAN_PURPOSE_ANALYSIS, FRAUD_DETECTION)),
            new AgentSpec(COMPREHENSIVE_RISK_ASSESSMENT, "综合风险 Agent",
                    List.of(SUBJECT_VERIFICATION, FINANCIAL_ANALYSIS, JUDICIAL_RISK, CREDIT_DEBT_ANALYSIS,
                            SITE_VISIT, DOCUMENT_RECOGNITION, INDUSTRY_EXPERT, LOAN_PURPOSE_ANALYSIS,
                            FRAUD_DETECTION, POLICY_MATCHING)),
            new AgentSpec(DUE_DILIGENCE_REPORT, "尽调报告 Agent",
                    List.of(SUBJECT_VERIFICATION, FINANCIAL_ANALYSIS, JUDICIAL_RISK, CREDIT_DEBT_ANALYSIS,
                            SITE_VISIT, DOCUMENT_RECOGNITION, INDUSTRY_EXPERT, LOAN_PURPOSE_ANALYSIS,
                            FRAUD_DETECTION, POLICY_MATCHING, COMPREHENSIVE_RISK_ASSESSMENT)));

    private static final Map<String, AgentSpec> BY_CODE;

    static {
        Map<String, AgentSpec> map = new LinkedHashMap<>();
        SPECS.forEach(spec -> map.put(spec.agentCode(), spec));
        BY_CODE = Map.copyOf(map);
    }

    private DdAgentCatalog() {
    }

    /** 全部 Agent，按规格文档顺序 */
    public static List<AgentSpec> all() {
        return SPECS;
    }

    /** 按编码查找，不存在返回空 */
    public static Optional<AgentSpec> find(String agentCode) {
        return Optional.ofNullable(agentCode == null ? null : BY_CODE.get(agentCode));
    }

    public static boolean contains(String agentCode) {
        return find(agentCode).isPresent();
    }

    /** 初始依赖关系，用于 LLM 未返回依赖计划时兜底 */
    public static Map<String, List<String>> defaultDependencies() {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        SPECS.forEach(spec -> dependencies.put(spec.agentCode(), spec.dependsOn()));
        return dependencies;
    }

    /** 可用 Agent 清单，供 Prompt 渲染 */
    public static String describeForPrompt() {
        StringBuilder builder = new StringBuilder();
        for (AgentSpec spec : SPECS) {
            builder.append("- ").append(spec.agentCode()).append("（").append(spec.agentName()).append("）");
            if (!spec.dependsOn().isEmpty()) {
                builder.append("；上游：").append(String.join("、", spec.dependsOn()));
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    /**
     * 按依赖关系计算拓扑层级，同层节点可并行；依赖不在节点集合内时忽略，存在环时返回空集合。
     */
    public static List<List<String>> levels(List<String> agentCodes, Map<String, List<String>> dependencies) {
        List<List<String>> levels = new ArrayList<>();
        Set<String> planned = new LinkedHashSet<>(agentCodes);
        Set<String> completed = new LinkedHashSet<>();
        List<String> remaining = new ArrayList<>(planned);
        while (!remaining.isEmpty()) {
            List<String> layer = remaining.stream()
                    .filter(code -> completed.containsAll(dependencies.getOrDefault(code, List.of()).stream()
                            .filter(planned::contains).toList()))
                    .toList();
            if (layer.isEmpty()) {
                return List.of();
            }
            levels.add(layer);
            completed.addAll(layer);
            remaining.removeAll(layer);
        }
        return levels;
    }
}
