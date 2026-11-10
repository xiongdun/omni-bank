package com.omnibank.service.agent.dd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.ai.AgentDataGap;
import com.omnibank.service.agent.dd.ai.AgentFinding;
import com.omnibank.service.agent.dd.ai.AgentResult;
import com.omnibank.service.agent.dd.ai.AgentRuntime;
import com.omnibank.service.agent.dd.ai.agent.SubjectVerificationData;
import com.omnibank.service.agent.dd.ai.agent.SubjectVerificationInput;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.dto.task.TaskCreateVO;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import com.omnibank.service.agent.dd.service.DdTaskService;
import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolRegistry;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import com.omnibank.service.agent.dd.tool.ToolResultStatus;
import com.omnibank.service.agent.dd.tool.impl.CreditCodeVerifyResult;
import com.omnibank.service.agent.dd.tool.impl.EnterpriseRegistrySnapshot;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 框架底座集成测试：工具注册/查找、规则工具、Mock 查询工具、Agent 运行时与主体核验 Agent
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@Transactional
class DdAgentFrameworkIntegrationTest {

    private static final String VALID_CREDIT_CODE = "91350100M000100Y43";

    private static final String OTHER_VALID_CREDIT_CODE = "91110108551385082Q";

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private DdTaskService taskService;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 工具注册表可发现工具且未知工具返回业务错误码() {
        assertThat(toolRegistry.codes()).contains("enterprise.queryEnterprise", "enterprise.verifyCreditCode");
        assertThat(toolRegistry.require("enterprise.queryEnterprise").getDefinition().isMock()).isTrue();
        assertThat(toolRegistry.require("enterprise.verifyCreditCode").getDefinition().isMock()).isFalse();

        assertThatThrownBy(() -> toolRegistry.require("enterprise.notExists"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230117"));
    }

    @Test
    void 统一社会信用代码校验覆盖长度字符集与校验位() {
        AgentTool<CreditCodeVerifyResult> tool = toolRegistry.require("enterprise.verifyCreditCode");

        assertThat(tool.execute(new ToolRequest(null, Map.of("creditCode", VALID_CREDIT_CODE))).getData().valid())
                .isTrue();

        CreditCodeVerifyResult tooShort = tool.execute(
                new ToolRequest(null, Map.of("creditCode", "91350100M000100Y4"))).getData();
        assertThat(tooShort.valid()).isFalse();
        assertThat(tooShort.reason()).contains("18");

        CreditCodeVerifyResult illegalChar = tool.execute(
                new ToolRequest(null, Map.of("creditCode", "91350100M000100YI3"))).getData();
        assertThat(illegalChar.valid()).isFalse();
        assertThat(illegalChar.reason()).contains("非法字符");

        CreditCodeVerifyResult wrongCheckDigit = tool.execute(
                new ToolRequest(null, Map.of("creditCode", "91350100M000100Y44"))).getData();
        assertThat(wrongCheckDigit.valid()).isFalse();
        assertThat(wrongCheckDigit.reason()).contains("校验位");
    }

    @Test
    void 工商查询工具返回Mock证据与来源标记() {
        long taskId = submittedTask();
        AgentTool<EnterpriseRegistrySnapshot> tool = toolRegistry.require("enterprise.queryEnterprise");

        ToolResult<EnterpriseRegistrySnapshot> result = tool.execute(new ToolRequest(taskId, Map.of()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.getEvidenceIds()).hasSize(1);
        assertThat(result.getMetadata()).containsEntry("mock", true);
        assertThat(result.getData().mock()).isTrue();
        assertThat(result.getData().sourceSystem()).startsWith("MOCK_");
    }

    @Test
    void 主体核验Agent返回结构化结果并标记Mock与数据缺口() {
        long taskId = submittedTask();

        AgentResult<SubjectVerificationData> result = agentRuntime.execute("SUBJECT_VERIFICATION", taskId,
                new SubjectVerificationInput(null, null));

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getAgentCode()).isEqualTo("SUBJECT_VERIFICATION");
        assertThat(result.getExecutionId()).isNotBlank();
        assertThat(result.getGeneratedAt()).isNotNull();
        assertThat(result.getEvidenceRefs()).hasSize(1);
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("Mock"));
        assertThat(result.getFindings()).isEmpty();
        assertThat(result.getDataGaps()).extracting(AgentDataGap::getDataCode)
                .contains("LEGAL_PERSON", "ENTERPRISE_BUSINESS_STATUS");
        assertThat(result.getData().getCreditCodeValid()).isTrue();
        assertThat(result.getData().getRegistryEvidenceFound()).isTrue();
        assertThat(result.getData().getEvidenceCompleteness()).isLessThan(BigDecimal.ONE);
        assertThat(result.getConfidence()).isBetween(new BigDecimal("0.70"), BigDecimal.ONE);
    }

    @Test
    void 申报主体不一致时产生待核实线索() {
        long taskId = submittedTask();

        AgentResult<SubjectVerificationData> result = agentRuntime.execute("SUBJECT_VERIFICATION", taskId,
                new SubjectVerificationInput("另一家科技有限公司", OTHER_VALID_CREDIT_CODE));

        assertThat(result.getFindings()).extracting(AgentFinding::getFindingCode)
                .contains("SUBJECT_NAME_MISMATCH", "SUBJECT_CREDIT_CODE_MISMATCH");
        assertThat(result.getFindings())
                .filteredOn(finding -> finding.getFindingCode().endsWith("MISMATCH"))
                .allMatch(finding -> "NEEDS_VERIFICATION".equals(finding.getStatus()));
        assertThat(result.getData().getNameConsistent()).isFalse();
        assertThat(result.getData().getCreditCodeConsistent()).isFalse();
    }

    @Test
    void 未知Agent返回业务错误码() {
        assertThatThrownBy(() -> agentRuntime.execute("NOT_EXISTS", 1L, null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode()).isEqualTo("230118"));
    }

    private long submittedTask() {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(VALID_CREDIT_CODE);
        enterprise.setEnterpriseName("江西主体核验测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo("APPAGT000001");
        application.setProductCode("CORPORATE_WORKING_CAPITAL");
        application.setProductName("流动资金贷款");
        application.setApplyAmount(new BigDecimal("5000000.00"));
        application.setApplyTerm(12);
        application.setTermUnit("MONTH");
        application.setLoanPurpose("采购原材料");
        application.setGuaranteeType("MORTGAGE");

        CreateTaskReq req = new CreateTaskReq();
        req.setEnterprise(enterprise);
        req.setApplication(application);

        TaskCreateVO created = taskService.create(req);
        taskService.submit(created.getTaskId());
        outboxPublisher.publishPending();
        return created.getTaskId();
    }
}
