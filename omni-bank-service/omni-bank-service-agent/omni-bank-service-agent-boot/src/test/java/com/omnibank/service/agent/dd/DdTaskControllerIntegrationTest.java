package com.omnibank.service.agent.dd;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibank.service.agent.dd.dto.task.CreateTaskReq;
import com.omnibank.service.agent.dd.dto.task.EnterpriseReq;
import com.omnibank.service.agent.dd.dto.task.LoanApplicationReq;
import com.omnibank.service.agent.dd.service.DdOutboxPublisher;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 尽调任务 HTTP 接口契约测试：路径映射、参数绑定、Result 包装与全局异常码
 */
@SpringBootTest(classes = DdTestApplication.class)
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@Transactional
class DdTaskControllerIntegrationTest {

    private static final String SUCCESS = "000200";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DdOutboxPublisher outboxPublisher;

    @Test
    void 创建任务接口返回成功且详情可查() throws Exception {
        long taskId = createTask("91360000CTRL000001", "APPCTRL000001");

        mockMvc.perform(get("/v1/dd/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.taskStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.taskStatusDesc").value("草稿"))
                .andExpect(jsonPath("$.data.enterprise.creditCode").value("91360000CTRL000001"))
                .andExpect(jsonPath("$.data.application.applicationNo").value("APPCTRL000001"))
                .andExpect(jsonPath("$.data.latestScenario").value(nullValue()));
    }

    @Test
    void 创建任务缺少必填项返回参数错误码() throws Exception {
        CreateTaskReq req = req("91360000CTRL000002", "APPCTRL000002");
        req.setEnterprise(null);

        mockMvc.perform(post("/v1/dd/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000400"))
                .andExpect(jsonPath("$.msg").value("企业信息不能为空"));
    }

    @Test
    void 分页接口绑定查询参数并返回匹配记录() throws Exception {
        long taskId = createTask("91360000CTRL000003", "APPCTRL000003");
        String taskNo = readTaskNo(taskId);

        mockMvc.perform(get("/v1/dd/tasks")
                        .param("taskNo", taskNo)
                        .param("taskStatus", "DRAFT")
                        .param("current", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.records[0].taskNo").value(taskNo))
                .andExpect(jsonPath("$.data.records[0].taskId").value(taskId))
                .andExpect(jsonPath("$.data.records[0].delFlag").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].creator").doesNotExist());
    }

    @Test
    void 提交任务后状态场景与上下文接口依次可用() throws Exception {
        long taskId = createTask("91360000CTRL000004", "APPCTRL000004");

        mockMvc.perform(post("/v1/dd/tasks/{taskId}/submit", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.taskStatus").value("INITIALIZING"));

        outboxPublisher.publishPending();

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/status", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.taskStatus").value("ANALYZING"))
                .andExpect(jsonPath("$.data.progress").value(60));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/scenario", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value("WORKING_CAPITAL"))
                .andExpect(jsonPath("$.data.identifyStatus").value("IDENTIFIED"))
                .andExpect(jsonPath("$.data.scenarioVersion").value(1));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/context", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditCode").value("91360000CTRL000004"))
                .andExpect(jsonPath("$.data.businessScenario.businessType").value("WORKING_CAPITAL"))
                .andExpect(jsonPath("$.data.contextVersion").value(1));
    }

    @Test
    void 重新识别场景接口生成递增版本() throws Exception {
        long taskId = createTask("91360000CTRL000005", "APPCTRL000005");
        mockMvc.perform(post("/v1/dd/tasks/{taskId}/submit", taskId))
                .andExpect(status().isOk());
        outboxPublisher.publishPending();

        mockMvc.perform(post("/v1/dd/tasks/{taskId}/scenario/reidentify", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.scenarioVersion").value(2));
    }

    @Test
    void 取消任务后不可再提交并返回业务错误码() throws Exception {
        long taskId = createTask("91360000CTRL000006", "APPCTRL000006");

        mockMvc.perform(post("/v1/dd/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS));

        mockMvc.perform(get("/v1/dd/tasks/{taskId}/status", taskId))
                .andExpect(jsonPath("$.data.taskStatus").value("CANCELLED"));

        mockMvc.perform(post("/v1/dd/tasks/{taskId}/submit", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("230102"));
    }

    @Test
    void 查询不存在的任务返回任务不存在错误码() throws Exception {
        mockMvc.perform(get("/v1/dd/tasks/{taskId}", 99999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("230101"));
    }

    private long createTask(String creditCode, String applicationNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/dd/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req(creditCode, applicationNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SUCCESS))
                .andExpect(jsonPath("$.data.taskId").isNumber())
                .andExpect(jsonPath("$.data.taskNo", startsWith("DD")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("taskId").asLong();
    }

    private String readTaskNo(long taskId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/dd/tasks/{taskId}", taskId)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("taskNo").asText();
    }

    private CreateTaskReq req(String creditCode, String applicationNo) {
        EnterpriseReq enterprise = new EnterpriseReq();
        enterprise.setCreditCode(creditCode);
        enterprise.setEnterpriseName("江西控制器测试科技有限公司");

        LoanApplicationReq application = new LoanApplicationReq();
        application.setApplicationNo(applicationNo);
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
        return req;
    }
}
