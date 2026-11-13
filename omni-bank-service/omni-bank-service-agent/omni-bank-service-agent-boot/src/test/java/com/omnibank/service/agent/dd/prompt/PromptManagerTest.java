package com.omnibank.service.agent.dd.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnibank.common.core.exception.BizException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Prompt 管理单元测试：版本发现、默认取最新版本、变量渲染与缺失校验。
 */
class PromptManagerTest {

    private final PromptManager promptManager = new PromptManager();

    @Test
    void 未指定版本时取最新版本() {
        PromptTemplate template = promptManager.require("SUBJECT_VERIFICATION", null);

        assertThat(template.promptCode()).isEqualTo("SUBJECT_VERIFICATION");
        assertThat(template.version()).isEqualTo("v1");
        assertThat(template.systemPrompt()).isNotBlank();
        assertThat(template.outputSchema()).isNotBlank();
    }

    @Test
    void 版本号支持带v前缀() {
        assertThat(promptManager.require("planner", "V1").version()).isEqualTo("v1");
    }

    @Test
    void 版本扫描按版本号升序且未知编码返回空() {
        assertThat(promptManager.versions("PLANNER")).containsExactly("v1");
        assertThat(promptManager.versions("NOT_EXISTS")).isEmpty();
    }

    @Test
    void 未知编码取模板抛未找到() {
        assertThatThrownBy(() -> promptManager.require("NOT_EXISTS", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登记任何版本");
    }

    @Test
    void 版本格式非法抛未找到() {
        assertThatThrownBy(() -> promptManager.require("PLANNER", "latest"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本格式非法");
    }

    @Test
    void 渲染替换全部变量() {
        RenderedPrompt rendered = promptManager.render("PLANNER", "v1", Map.ofEntries(
                Map.entry("enterpriseName", "示例科技有限公司"),
                Map.entry("creditCode", "91310000MA1FL0000X"),
                Map.entry("industryName", "制造业"),
                Map.entry("businessScenario", "流动资金贷款"),
                Map.entry("subScenario", "原材料采购"),
                Map.entry("scenarioConfidence", "0.90"),
                Map.entry("scenarioStatus", "IDENTIFIED"),
                Map.entry("productCode", "CORPORATE_WORKING_CAPITAL"),
                Map.entry("productName", "流动资金贷款"),
                Map.entry("loanPurpose", "原材料采购"),
                Map.entry("applyAmount", "5000000"),
                Map.entry("guaranteeType", "抵押"),
                Map.entry("evidenceSummary", "证据 3 条"),
                Map.entry("availableAgents", "SUBJECT_VERIFICATION, FINANCIAL_ANALYSIS")));

        assertThat(rendered.userPrompt()).contains("示例科技有限公司").doesNotContain("{{");
    }

    @Test
    void 变量缺失时拒绝渲染() {
        assertThatThrownBy(() -> promptManager.render("SUBJECT_VERIFICATION", "v1",
                Map.of("enterpriseName", "示例科技有限公司")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Prompt 变量缺失");
    }
}
