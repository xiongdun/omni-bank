package com.omnibank.service.agent.dd.prompt;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.api.enums.DdErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Prompt Manager：按 Agent 独立管理 Prompt 模板，负责版本发现、加载、变量渲染与输出 Schema 读取。
 * 模板只从 classpath 加载，历史版本以目录形式保留，不允许覆盖已在生产使用的版本。
 */
@Component
public class PromptManager {

    private static final String BASE_PATH = "dd/prompts/";

    private static final String SYSTEM_PROMPT_FILE = "system-prompt.txt";

    private static final String USER_TEMPLATE_FILE = "user-template.txt";

    private static final String OUTPUT_SCHEMA_FILE = "output-schema.json";

    /** 模板变量语法：{{variableName}} */
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    private static final Pattern VERSION_IN_PATH = Pattern.compile("/v(\\d+)/" + SYSTEM_PROMPT_FILE + "$");

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    /**
     * 读取指定版本模板；版本为空时取最新版本。模板不存在抛 230120。
     */
    public PromptTemplate require(String promptCode, String version) {
        String folder = folderOf(promptCode);
        String resolvedVersion = normalizeVersion(promptCode, version);
        return cache.computeIfAbsent(folder + "/" + resolvedVersion,
                key -> load(promptCode, folder, resolvedVersion));
    }

    /**
     * 渲染用户模板；变量缺失抛 230121，避免把未填充的 Prompt 提交给模型。
     */
    public RenderedPrompt render(String promptCode, String version, Map<String, String> variables) {
        PromptTemplate template = require(promptCode, version);
        return new RenderedPrompt(template.promptCode(), template.version(),
                renderTemplate(template.systemPrompt(), variables),
                renderTemplate(template.userTemplate(), variables));
    }

    /**
     * 已登记的模板版本，按版本号升序返回；目录不存在返回空集合。
     */
    public List<String> versions(String promptCode) {
        String folder = folderOf(promptCode);
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(
                    "classpath*:" + BASE_PATH + folder + "/v*/" + SYSTEM_PROMPT_FILE);
        } catch (IOException e) {
            throw new BizException(DdErrorCode.PROMPT_NOT_FOUND,
                    "Prompt 版本扫描失败：" + promptCode);
        }
        List<String> versions = new ArrayList<>();
        for (Resource resource : resources) {
            String path;
            try {
                path = resource.getURL().toString().replace('\\', '/');
            } catch (IOException e) {
                continue;
            }
            Matcher matcher = VERSION_IN_PATH.matcher(path);
            if (!matcher.find()) {
                continue;
            }
            String version = "v" + matcher.group(1);
            if (!versions.contains(version)) {
                versions.add(version);
            }
        }
        versions.sort(Comparator.comparingInt(PromptManager::versionNumber));
        return versions;
    }

    private PromptTemplate load(String promptCode, String folder, String version) {
        String base = BASE_PATH + folder + "/" + version + "/";
        String systemPrompt = read(base + SYSTEM_PROMPT_FILE, promptCode, version);
        String userTemplate = read(base + USER_TEMPLATE_FILE, promptCode, version);
        String outputSchema = read(base + OUTPUT_SCHEMA_FILE, promptCode, version);
        return new PromptTemplate(promptCode, version, systemPrompt, userTemplate, outputSchema);
    }

    private String read(String path, String promptCode, String version) {
        Resource resource = resourceResolver.getResource("classpath:" + path);
        if (!resource.exists()) {
            throw new BizException(DdErrorCode.PROMPT_NOT_FOUND,
                    "Prompt 模板不存在：" + promptCode + " / " + version + " / " + path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(DdErrorCode.PROMPT_NOT_FOUND,
                    "Prompt 模板读取失败：" + promptCode + " / " + version);
        }
    }

    private String normalizeVersion(String promptCode, String version) {
        if (version == null || version.isBlank()) {
            List<String> versions = versions(promptCode);
            if (versions.isEmpty()) {
                throw new BizException(DdErrorCode.PROMPT_NOT_FOUND, "Prompt 未登记任何版本：" + promptCode);
            }
            return versions.get(versions.size() - 1);
        }
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("\\d+")) {
            throw new BizException(DdErrorCode.PROMPT_NOT_FOUND,
                    "Prompt 版本格式非法：" + promptCode + " / " + version);
        }
        return "v" + normalized;
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = variables == null ? null : variables.get(variableName);
            if (value == null) {
                throw new BizException(DdErrorCode.PROMPT_RENDER_FAILED,
                        "Prompt 变量缺失：" + variableName);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** Agent 编码 -> 模板目录：大写蛇形转小写中划线，如 SUBJECT_VERIFICATION -> subject-verification */
    private String folderOf(String promptCode) {
        if (promptCode == null || promptCode.isBlank()) {
            throw new BizException(DdErrorCode.PROMPT_NOT_FOUND, "Prompt 编码为空");
        }
        return promptCode.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static int versionNumber(String version) {
        return Integer.parseInt(version.substring(1));
    }
}
