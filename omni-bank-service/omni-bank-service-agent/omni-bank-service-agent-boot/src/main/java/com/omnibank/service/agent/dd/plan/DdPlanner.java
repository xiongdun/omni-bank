package com.omnibank.service.agent.dd.plan;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.config.DdLlmProperties;
import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import com.omnibank.service.agent.dd.entity.DdEnterprise;
import com.omnibank.service.agent.dd.entity.DdEvidence;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;
import com.omnibank.service.agent.dd.entity.DdPlan;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.EvidenceType;
import com.omnibank.service.agent.dd.enums.PlannerMode;
import com.omnibank.service.agent.dd.llm.DdChatService;
import com.omnibank.service.agent.dd.mapper.DdEvidenceMapper;
import com.omnibank.service.agent.dd.mapper.DdPlanMapper;
import com.omnibank.service.agent.dd.prompt.PromptManager;
import com.omnibank.service.agent.dd.prompt.RenderedPrompt;
import com.omnibank.service.agent.dd.service.DdEnterpriseService;
import com.omnibank.service.agent.dd.service.DdLoanApplicationService;
import com.omnibank.service.agent.dd.service.DdScenarioService;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 尽调 Planner：大模型优先生成计划，模型处于 MOCK 模式时按 Agent 目录规则生成并显式标记。
 * 计划按版本落库，任务 current_plan_id 指向最新版本；LLM 输出必须通过 Agent 目录与依赖校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DdPlanner {

    /** Prompt 编码，对应 classpath:dd/prompts/planner */
    private static final String PROMPT_CODE = "PLANNER";

    private static final String MODE_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private static final String RULE_MODEL = "MOCK_RULE";

    private static final String RULE_PROMPT_VERSION = "rule-1.0";

    private static final String CONSTANT_FAIL_FAST = "failFast";

    /** 规则计划的必需数据，与 EvidenceType 对齐 */
    private static final List<String> REQUIRED_DATA = List.of(
            EvidenceType.ENTERPRISE_PROFILE.name(),
            EvidenceType.FINANCIAL_STATEMENT.name(),
            EvidenceType.CREDIT_REPORT.name(),
            EvidenceType.JUDICIAL_RECORD.name(),
            EvidenceType.SITE_INVESTIGATION.name(),
            EvidenceType.CUSTOMER_DOCUMENT.name(),
            EvidenceType.POLICY_DOCUMENT.name());

    private static final List<String> REQUIRED_DOCUMENTS = List.of(
            "BUSINESS_LICENSE", "ARTICLES_OF_ASSOCIATION", "FINANCIAL_STATEMENT",
            "BANK_STATEMENT", "LOAN_APPLICATION_FORM", "LEGAL_PERSON_ID");

    private static final List<String> OPTIONAL_DOCUMENTS = List.of(
            "AUDIT_REPORT", "PURCHASE_CONTRACT", "SALES_CONTRACT", "TAX_PAYMENT_CERTIFICATE");

    private final DdLlmProperties llmProperties;

    private final PromptManager promptManager;

    private final DdChatService chatService;

    private final DdTaskService taskService;

    private final DdScenarioService scenarioService;

    private final DdEnterpriseService enterpriseService;

    private final DdLoanApplicationService applicationService;

    private final DdEvidenceMapper evidenceMapper;

    private final DdPlanMapper planMapper;

    private final DdJsonSupport jsonSupport;

    /**
     * 生成并保存一份新版本计划，返回落库后的计划。
     */
    public DdPlan plan(Long taskId) {
        DdTask task = taskService.requireTask(taskId);
        DdEnterprise enterprise = enterpriseService.getById(task.getEnterpriseId());
        if (enterprise == null) {
            throw new BizException(DdErrorCode.ENTERPRISE_NOT_FOUND);
        }
        DdLoanApplication application = applicationService.requireByTaskId(taskId);
        ScenarioVO scenario = scenarioService.latestOrNull(taskId);
        List<DdEvidence> evidences = evidenceMapper.selectList(Wrappers.<DdEvidence>lambdaQuery()
                .eq(DdEvidence::getTaskId, taskId)
                .orderByAsc(DdEvidence::getId)
                .last("LIMIT 20"));

        GeneratedPlan generated = useLlm()
                ? generateByLlm(enterprise, application, scenario, evidences)
                : new GeneratedPlan(ruleOutput(scenario, evidences), PlannerMode.MOCK, RULE_MODEL,
                        RULE_PROMPT_VERSION);

        DdPlan plan = persist(taskId, generated);
        taskService.updateCurrentPlan(taskId, plan.getId());
        log.info("尽调计划生成完成 taskId={}, planId={}, version={}, mode={}, agents={}",
                taskId, plan.getId(), plan.getPlanVersion(), plan.getPlannerMode(),
                generated.output().getRequiredAgents().size());
        return plan;
    }

    private boolean useLlm() {
        return MODE_OPENAI_COMPATIBLE.equalsIgnoreCase(llmProperties.getMode());
    }

    private GeneratedPlan generateByLlm(DdEnterprise enterprise, DdLoanApplication application,
                                        ScenarioVO scenario, List<DdEvidence> evidences) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("enterpriseName", nullToEmpty(enterprise.getEnterpriseName()));
        variables.put("creditCode", nullToEmpty(enterprise.getCreditCode()));
        variables.put("industryName", nullToEmpty(enterprise.getIndustryName()));
        variables.put("businessScenario", scenario == null ? "" : nullToEmpty(scenario.getBusinessType()));
        variables.put("subScenario", scenario == null ? "" : nullToEmpty(scenario.getLoanPurposeType()));
        variables.put("scenarioConfidence",
                scenario == null || scenario.getConfidence() == null ? "" : scenario.getConfidence().toPlainString());
        variables.put("scenarioStatus", scenario == null ? "" : nullToEmpty(scenario.getIdentifyStatus()));
        variables.put("productCode", nullToEmpty(application.getProductCode()));
        variables.put("productName", nullToEmpty(application.getProductName()));
        variables.put("loanPurpose", nullToEmpty(application.getLoanPurpose()));
        variables.put("applyAmount",
                application.getApplyAmount() == null ? "" : application.getApplyAmount().toPlainString());
        variables.put("guaranteeType", nullToEmpty(application.getGuaranteeType()));
        variables.put("evidenceSummary", evidenceSummary(evidences));
        variables.put("availableAgents", DdAgentCatalog.describeForPrompt());

        RenderedPrompt rendered = promptManager.render(PROMPT_CODE, null, variables);
        PlannerOutput output = chatService.completeForEntity(rendered.systemPrompt(), rendered.userPrompt(),
                new ParameterizedTypeReference<PlannerOutput>() {
                });
        if (output == null) {
            throw new BizException(DdErrorCode.PLANNER_GENERATE_FAILED, "大模型未返回尽调计划");
        }
        normalizeAndValidate(output);
        return new GeneratedPlan(output, PlannerMode.LLM, llmProperties.getModel(), rendered.version());
    }

    /** MOCK 模式：按 Agent 目录全量编排，顺序与依赖来自目录，结果显式标记为规则生成 */
    private PlannerOutput ruleOutput(ScenarioVO scenario, List<DdEvidence> evidences) {
        PlannerOutput output = new PlannerOutput();
        output.setBusinessScenario(scenario == null || !StringUtils.hasText(scenario.getBusinessType())
                ? "OTHER" : scenario.getBusinessType());
        output.setSubScenario(scenario == null ? null : scenario.getLoanPurposeType());
        output.setConfidence(scenario == null || scenario.getConfidence() == null
                ? new BigDecimal("0.30") : scenario.getConfidence());
        output.setScenarioStatus(scenario == null ? "NEEDS_CONFIRMATION" : scenario.getIdentifyStatus());
        output.setReasoningSummary("规则生成计划（模型 MOCK 模式）：按 Agent 目录全量编排，证据 "
                + evidences.size() + " 条，其中 Mock 证据 " + mockEvidenceCount(evidences)
                + " 条，结论仅用于链路验证");

        List<String> agents = DdAgentCatalog.all().stream().map(DdAgentCatalog.AgentSpec::agentCode).toList();
        output.setRequiredAgents(new ArrayList<>(agents));
        output.setDependencyPlan(new LinkedHashMap<>(DdAgentCatalog.defaultDependencies()));
        output.setRequiredData(new ArrayList<>(REQUIRED_DATA));
        output.setRequiredDocuments(new ArrayList<>(REQUIRED_DOCUMENTS));
        output.setOptionalDocuments(new ArrayList<>(OPTIONAL_DOCUMENTS));
        output.setSupplementPlan(missingEvidence(evidences));
        output.setUnknowns(scenario == null || scenario.getUncertainItems() == null
                ? new ArrayList<>() : new ArrayList<>(scenario.getUncertainItems()));

        Map<String, Object> executionConfig = new LinkedHashMap<>();
        executionConfig.put(CONSTANT_FAIL_FAST, false);
        executionConfig.put("parallelGroups", DdAgentCatalog.levels(agents, output.getDependencyPlan()));
        output.setExecutionConfig(executionConfig);
        return output;
    }

    /** 补全依赖缺省值并校验：Agent 必须属于目录、依赖必须闭包内且无环、置信度合法 */
    private void normalizeAndValidate(PlannerOutput output) {
        if (output.getRequiredAgents() == null) {
            output.setRequiredAgents(new ArrayList<>());
        }
        if (output.getRequiredData() == null) {
            output.setRequiredData(new ArrayList<>());
        }
        if (output.getRequiredDocuments() == null) {
            output.setRequiredDocuments(new ArrayList<>());
        }
        if (output.getOptionalDocuments() == null) {
            output.setOptionalDocuments(new ArrayList<>());
        }
        if (output.getSupplementPlan() == null) {
            output.setSupplementPlan(new ArrayList<>());
        }
        if (output.getUnknowns() == null) {
            output.setUnknowns(new ArrayList<>());
        }
        if (!StringUtils.hasText(output.getBusinessScenario())) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "计划缺少业务场景");
        }
        BigDecimal confidence = output.getConfidence();
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "计划场景置信度非法：" + confidence);
        }

        Set<String> agents = new LinkedHashSet<>();
        for (String agent : output.getRequiredAgents()) {
            if (!StringUtils.hasText(agent)) {
                continue;
            }
            String code = agent.trim();
            if (!DdAgentCatalog.contains(code)) {
                throw new BizException(DdErrorCode.PLAN_INVALID, "计划包含未登记的 Agent：" + code);
            }
            agents.add(code);
        }
        if (agents.isEmpty()) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "计划未包含任何 Agent");
        }
        output.setRequiredAgents(new ArrayList<>(agents));

        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        Map<String, List<String>> planned = output.getDependencyPlan();
        for (String agent : agents) {
            List<String> declared = planned == null ? null : planned.get(agent);
            List<String> normalized = new ArrayList<>();
            if (declared != null) {
                for (String dependency : declared) {
                    if (!StringUtils.hasText(dependency)) {
                        continue;
                    }
                    String code = dependency.trim();
                    if (!agents.contains(code)) {
                        throw new BizException(DdErrorCode.PLAN_INVALID,
                                "计划依赖引用了未执行的 Agent：" + agent + " -> " + code);
                    }
                    if (code.equals(agent)) {
                        throw new BizException(DdErrorCode.PLAN_INVALID, "Agent 不能依赖自身：" + agent);
                    }
                    if (!normalized.contains(code)) {
                        normalized.add(code);
                    }
                }
            }
            dependencies.put(agent, normalized);
        }
        if (planned != null) {
            for (String key : planned.keySet()) {
                if (!agents.contains(key)) {
                    throw new BizException(DdErrorCode.PLAN_INVALID, "依赖计划包含未执行的 Agent：" + key);
                }
            }
        }
        if (DdAgentCatalog.levels(new ArrayList<>(agents), dependencies).isEmpty()) {
            throw new BizException(DdErrorCode.PLAN_INVALID, "计划依赖存在循环");
        }
        output.setDependencyPlan(dependencies);

        if (output.getExecutionConfig() == null) {
            output.setExecutionConfig(new LinkedHashMap<>());
        }
        output.getExecutionConfig().putIfAbsent(CONSTANT_FAIL_FAST, false);
    }

    private DdPlan persist(Long taskId, GeneratedPlan generated) {
        PlannerOutput output = generated.output();
        DdPlan plan = new DdPlan();
        plan.setTaskId(taskId);
        plan.setPlanVersion(nextVersion(taskId));
        plan.setPlannerMode(generated.mode().name());
        plan.setBusinessScenario(output.getBusinessScenario());
        plan.setSubScenario(output.getSubScenario());
        plan.setConfidence(output.getConfidence());
        plan.setReasoningSummary(output.getReasoningSummary());
        plan.setAgentPlan(jsonSupport.write(output.getRequiredAgents()));
        plan.setDataPlan(jsonSupport.write(output.getRequiredData()));
        Map<String, Object> materialPlan = new LinkedHashMap<>();
        materialPlan.put("required", output.getRequiredDocuments());
        materialPlan.put("optional", output.getOptionalDocuments());
        plan.setMaterialPlan(jsonSupport.write(materialPlan));
        plan.setDependencyPlan(jsonSupport.write(output.getDependencyPlan()));
        plan.setSupplementPlan(jsonSupport.write(output.getSupplementPlan()));
        plan.setExecutionConfig(jsonSupport.write(output.getExecutionConfig()));
        plan.setModelName(generated.modelName());
        plan.setPromptVersion(generated.promptVersion());
        plan.setVersion(0);
        planMapper.insert(plan);
        return plan;
    }

    private int nextVersion(Long taskId) {
        DdPlan latest = planMapper.selectOne(Wrappers.<DdPlan>lambdaQuery()
                .eq(DdPlan::getTaskId, taskId)
                .orderByDesc(DdPlan::getPlanVersion)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getPlanVersion() + 1;
    }

    private String evidenceSummary(List<DdEvidence> evidences) {
        if (evidences.isEmpty()) {
            return "- 暂无已采集证据";
        }
        StringBuilder builder = new StringBuilder();
        for (DdEvidence evidence : evidences) {
            builder.append("- ").append(evidence.getEvidenceType())
                    .append("（来源：").append(nullToEmpty(evidence.getSourceSystem()))
                    .append("，Mock：").append(Boolean.TRUE.equals(evidence.getIsMock()) ? "是" : "否")
                    .append("）：").append(nullToEmpty(evidence.getContentSummary())).append('\n');
        }
        return builder.toString();
    }

    private List<String> missingEvidence(List<DdEvidence> evidences) {
        Set<String> present = new LinkedHashSet<>();
        for (DdEvidence evidence : evidences) {
            if (evidence.getEvidenceType() != null) {
                present.add(evidence.getEvidenceType());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_DATA) {
            if (!present.contains(required)) {
                missing.add("缺少证据：" + required);
            }
        }
        return missing;
    }

    private long mockEvidenceCount(List<DdEvidence> evidences) {
        return evidences.stream().filter(evidence -> Boolean.TRUE.equals(evidence.getIsMock())).count();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 计划生成结果：内容与生成方式必须一起记录，避免规则结果被误认为模型输出 */
    private record GeneratedPlan(PlannerOutput output, PlannerMode mode, String modelName,
                                 String promptVersion) {
    }
}
