package com.omnibank.service.agent.dd.tool.impl;

import com.omnibank.service.agent.dd.tool.AgentTool;
import com.omnibank.service.agent.dd.tool.ToolDefinition;
import com.omnibank.service.agent.dd.tool.ToolRequest;
import com.omnibank.service.agent.dd.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 统一社会信用代码校验工具（规则类）：按 GB 32100-2015 校验长度、字符集与校验位，
 * 属于确定性判断，不依赖外部接口，因此不是 Mock 工具。
 */
@Component
public class CreditCodeVerifyTool implements AgentTool<CreditCodeVerifyResult> {

    public static final String TOOL_CODE = "enterprise.verifyCreditCode";

    /** GB 32100-2015 允许的字符集（去除 I、O、S、V、Z） */
    private static final String CHARSET = "0123456789ABCDEFGHJKLMNPQRTUWXY";

    private static final int[] WEIGHTS = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};

    private static final int CODE_LENGTH = 18;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.of(TOOL_CODE, "统一社会信用代码校验",
                "按 GB 32100-2015 校验统一社会信用代码长度、字符集与校验位", "RULE", false, 1000);
    }

    @Override
    public ToolResult<CreditCodeVerifyResult> execute(ToolRequest request) {
        return ToolResult.success(TOOL_CODE, verify(request.stringParam("creditCode")), List.of(), Map.of());
    }

    private CreditCodeVerifyResult verify(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return new CreditCodeVerifyResult(false, "统一社会信用代码为空");
        }
        String code = rawCode.trim().toUpperCase();
        if (code.length() != CODE_LENGTH) {
            return new CreditCodeVerifyResult(false, "统一社会信用代码长度必须为 18 位");
        }
        int sum = 0;
        for (int i = 0; i < CODE_LENGTH - 1; i++) {
            int index = CHARSET.indexOf(code.charAt(i));
            if (index < 0) {
                return new CreditCodeVerifyResult(false, "统一社会信用代码包含非法字符：" + code.charAt(i));
            }
            sum += index * WEIGHTS[i];
        }
        int checkIndex = CHARSET.indexOf(code.charAt(CODE_LENGTH - 1));
        if (checkIndex < 0) {
            return new CreditCodeVerifyResult(false, "统一社会信用代码校验位非法");
        }
        int expectedIndex = (31 - sum % 31) % 31;
        if (checkIndex != expectedIndex) {
            return new CreditCodeVerifyResult(false, "统一社会信用代码校验位不匹配");
        }
        return new CreditCodeVerifyResult(true, "校验通过");
    }
}
