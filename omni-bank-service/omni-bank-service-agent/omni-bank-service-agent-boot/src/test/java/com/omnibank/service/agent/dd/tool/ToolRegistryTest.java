package com.omnibank.service.agent.dd.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnibank.common.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tool 注册表单元测试：重复编码启动期失败、缺失编码抛业务异常。
 */
class ToolRegistryTest {

    @Test
    void 按注册顺序返回工具编码() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("enterprise.query"), tool("financial.statements")));

        assertThat(registry.codes()).containsExactly("enterprise.query", "financial.statements");
        assertThat(registry.definitions()).extracting(ToolDefinition::getToolCode)
                .containsExactly("enterprise.query", "financial.statements");
    }

    @Test
    void 重复编码直接失败() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(tool("dup.tool"), tool("dup.tool"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup.tool");
    }

    @Test
    void 查询未注册工具抛业务异常() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("enterprise.query")));

        assertThatThrownBy(() -> registry.require("not.exists"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工具不存在");
    }

    @Test
    void 已注册工具可正常获取() {
        AgentTool<Object> tool = tool("enterprise.query");
        ToolRegistry registry = new ToolRegistry(List.of(tool));

        assertThat(registry.require("enterprise.query")).isSameAs(tool);
    }

    private AgentTool<Object> tool(String toolCode) {
        return new AgentTool<>() {
            private final ToolDefinition definition =
                    ToolDefinition.of(toolCode, toolCode, "测试工具", "QUERY", true, 1000L);

            @Override
            public ToolDefinition getDefinition() {
                return definition;
            }

            @Override
            public ToolResult<Object> execute(ToolRequest request) {
                return new ToolResult<>();
            }
        };
    }
}
