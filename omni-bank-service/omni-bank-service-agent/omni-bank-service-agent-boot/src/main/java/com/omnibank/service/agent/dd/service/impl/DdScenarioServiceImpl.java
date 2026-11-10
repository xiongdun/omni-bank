package com.omnibank.service.agent.dd.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import com.omnibank.service.agent.dd.config.DdScenarioProperties;
import com.omnibank.service.agent.dd.dto.scenario.ScenarioVO;
import com.omnibank.service.agent.dd.entity.DdBusinessScenario;
import com.omnibank.service.agent.dd.entity.DdEnterprise;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;
import com.omnibank.service.agent.dd.entity.DdTask;
import com.omnibank.service.agent.dd.enums.BusinessType;
import com.omnibank.service.agent.dd.enums.ScenarioIdentifyStatus;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import com.omnibank.service.agent.dd.mapper.DdBusinessScenarioMapper;
import com.omnibank.service.agent.dd.mapper.DdTaskMapper;
import com.omnibank.service.agent.dd.service.DdEnterpriseService;
import com.omnibank.service.agent.dd.service.DdLoanApplicationService;
import com.omnibank.service.agent.dd.service.DdScenarioService;
import com.omnibank.service.agent.dd.support.DdJsonSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 业务场景识别实现：产品编码与用途关键词双维度匹配，结果必须带置信度，低置信度只标记待确认
 */
@Service
@RequiredArgsConstructor
public class DdScenarioServiceImpl extends ServiceImpl<DdBusinessScenarioMapper, DdBusinessScenario>
        implements DdScenarioService {

    /** 规则未配置置信度时的缺省值 */
    private static final BigDecimal DEFAULT_RULE_CONFIDENCE = new BigDecimal("0.85");

    /** 允许人工重新识别的任务状态 */
    private static final List<TaskStatus> REIDENTIFY_ALLOWED = List.of(
            TaskStatus.DRAFT, TaskStatus.PLANNING, TaskStatus.DATA_COLLECTING,
            TaskStatus.ANALYZING, TaskStatus.WAITING_FOR_SUPPLEMENT, TaskStatus.RETURNED);

    private final DdTaskMapper taskMapper;

    private final DdEnterpriseService enterpriseService;

    private final DdLoanApplicationService applicationService;

    private final DdScenarioProperties properties;

    private final DdJsonSupport jsonSupport;

    @Override
    public ScenarioVO latest(Long taskId) {
        DdBusinessScenario scenario = latestEntity(taskId);
        if (scenario == null) {
            throw new BizException(DdErrorCode.SCENARIO_NOT_FOUND);
        }
        return convert(scenario);
    }

    @Override
    public ScenarioVO latestOrNull(Long taskId) {
        DdBusinessScenario scenario = latestEntity(taskId);
        return scenario == null ? null : convert(scenario);
    }

    @Override
    public ScenarioVO identify(Long taskId) {
        DdTask task = requireTask(taskId);
        DdLoanApplication application = applicationService.requireByTaskId(taskId);
        DdEnterprise enterprise = enterpriseService.getById(task.getEnterpriseId());

        MatchResult match = match(application, enterprise);

        DdBusinessScenario scenario = new DdBusinessScenario();
        scenario.setTaskId(taskId);
        scenario.setScenarioVersion(nextVersion(taskId));
        scenario.setBusinessType(match.businessType());
        scenario.setProductType(application.getProductCode());
        scenario.setIndustryCode(enterprise.getIndustryCode());
        scenario.setIndustryName(enterprise.getIndustryName());
        scenario.setLoanPurposeType(match.subScenario());
        scenario.setGuaranteeTypes(jsonSupport.write(
                StringUtils.hasText(application.getGuaranteeType())
                        ? List.of(application.getGuaranteeType()) : List.of()));
        scenario.setConfidence(match.confidence());
        scenario.setIdentifyStatus(match.identifyStatus().name());
        scenario.setScenarioDetail(jsonSupport.write(match.detail()));
        scenario.setEvidenceIds(jsonSupport.write(List.of()));
        scenario.setUncertainItems(jsonSupport.write(match.uncertainItems()));
        scenario.setVersion(0);
        save(scenario);
        return convert(scenario);
    }

    @Override
    public ScenarioVO reidentify(Long taskId) {
        DdTask task = requireTask(taskId);
        TaskStatus status = TaskStatus.valueOf(task.getTaskStatus());
        if (!REIDENTIFY_ALLOWED.contains(status)) {
            throw new BizException(DdErrorCode.TASK_STATUS_NOT_ALLOWED,
                    "任务当前状态不支持重新识别业务场景");
        }
        return identify(taskId);
    }

    @Override
    public ScenarioVO convert(DdBusinessScenario entity) {
        ScenarioVO vo = new ScenarioVO();
        vo.setId(entity.getId());
        vo.setScenarioVersion(entity.getScenarioVersion());
        vo.setBusinessType(entity.getBusinessType());
        vo.setBusinessTypeDesc(descOf(entity.getBusinessType()));
        vo.setProductType(entity.getProductType());
        vo.setIndustryCode(entity.getIndustryCode());
        vo.setIndustryName(entity.getIndustryName());
        vo.setLoanPurposeType(entity.getLoanPurposeType());
        vo.setFinancingMode(entity.getFinancingMode());
        vo.setGuaranteeTypes(jsonSupport.readStringList(entity.getGuaranteeTypes()));
        vo.setConfidence(entity.getConfidence());
        vo.setIdentifyStatus(entity.getIdentifyStatus());
        vo.setScenarioDetail(jsonSupport.readMap(entity.getScenarioDetail()));
        vo.setEvidenceIds(jsonSupport.readStringList(entity.getEvidenceIds()));
        vo.setUncertainItems(jsonSupport.readStringList(entity.getUncertainItems()));
        vo.setModelName(entity.getModelName());
        vo.setPromptVersion(entity.getPromptVersion());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private DdTask requireTask(Long taskId) {
        DdTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(DdErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /** 版本号在任务内自增，历史识别结果保留 */
    private int nextVersion(Long taskId) {
        DdBusinessScenario latest = latestEntity(taskId);
        return latest == null ? 1 : latest.getScenarioVersion() + 1;
    }

    private DdBusinessScenario latestEntity(Long taskId) {
        return lambdaQuery()
                .eq(DdBusinessScenario::getTaskId, taskId)
                .orderByDesc(DdBusinessScenario::getScenarioVersion)
                .last("LIMIT 1")
                .one();
    }

    private MatchResult match(DdLoanApplication application, DdEnterprise enterprise) {
        DdScenarioProperties.Rule best = null;
        String bestBasis = null;
        BigDecimal bestConfidence = null;
        for (DdScenarioProperties.Rule rule : properties.getRules()) {
            boolean productHit = StringUtils.hasText(application.getProductCode())
                    && rule.getProductCodes().contains(application.getProductCode());
            boolean keywordHit = StringUtils.hasText(application.getLoanPurpose())
                    && rule.getPurposeKeywords().stream().anyMatch(application.getLoanPurpose()::contains);
            if (!productHit && !keywordHit) {
                continue;
            }
            BigDecimal confidence = rule.getConfidence() == null ? DEFAULT_RULE_CONFIDENCE : rule.getConfidence();
            if (bestConfidence == null || confidence.compareTo(bestConfidence) > 0) {
                best = rule;
                bestConfidence = confidence;
                bestBasis = productHit && keywordHit ? "PRODUCT_AND_PURPOSE"
                        : productHit ? "PRODUCT_CODE" : "PURPOSE_KEYWORD";
            }
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("productCode", application.getProductCode());
        detail.put("loanPurpose", application.getLoanPurpose());
        detail.put("matchBasis", bestBasis);
        detail.put("matchedRule", best == null ? null : best.getName());
        detail.put("confidenceThreshold", properties.getConfidenceThreshold());

        if (best == null) {
            List<String> uncertain = List.of("未匹配到业务场景规则，需客户经理确认业务场景");
            detail.put("subScenario", null);
            return new MatchResult(BusinessType.OTHER.name(), null, properties.getUnmatchedConfidence(),
                    ScenarioIdentifyStatus.NEEDS_CONFIRMATION, detail, uncertain);
        }

        BusinessType businessType = parseBusinessType(best.getBusinessType());
        detail.put("subScenario", best.getSubScenario());
        boolean lowConfidence = bestConfidence.compareTo(properties.getConfidenceThreshold()) < 0;
        List<String> uncertain = new ArrayList<>();
        if (lowConfidence) {
            uncertain.add("识别置信度 " + bestConfidence + " 低于阈值 " + properties.getConfidenceThreshold()
                    + "，需客户经理确认业务场景");
        }
        return new MatchResult(businessType.name(), best.getSubScenario(), bestConfidence,
                lowConfidence ? ScenarioIdentifyStatus.NEEDS_CONFIRMATION : ScenarioIdentifyStatus.IDENTIFIED,
                detail, uncertain);
    }

    private BusinessType parseBusinessType(String value) {
        try {
            return BusinessType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException(DdErrorCode.SCENARIO_IDENTIFY_FAILED,
                    "业务场景规则配置非法：businessType=" + value);
        }
    }

    private String descOf(String businessType) {
        try {
            return businessType == null ? null : BusinessType.valueOf(businessType).getDesc();
        } catch (IllegalArgumentException e) {
            return businessType;
        }
    }

    /** 规则匹配结果，仅在方法内部流转 */
    private record MatchResult(String businessType, String subScenario, BigDecimal confidence,
                               ScenarioIdentifyStatus identifyStatus, Map<String, Object> detail,
                               List<String> uncertainItems) {
    }
}
