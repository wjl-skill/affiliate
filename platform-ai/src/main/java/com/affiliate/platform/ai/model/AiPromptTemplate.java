package com.affiliate.platform.ai.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化 Prompt 模板定义 (AI Prompt Template)
 */
public record AiPromptTemplate(
        String id,
        String tenantId,
        String templateKey,
        String title,
        String systemPrompt,
        String userPromptTemplate,
        String modelPreference,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public static AiPromptTemplate create(
            String tenantId,
            String templateKey,
            String title,
            String systemPrompt,
            String userPromptTemplate
    ) {
        return new AiPromptTemplate(
                "ptpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tenantId,
                templateKey,
                title,
                systemPrompt,
                userPromptTemplate,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * 将模板参数 {{key}} 动态渲染为实际值
     */
    public String renderUserPrompt(Map<String, Object> variables) {
        if (userPromptTemplate == null || variables == null || variables.isEmpty()) {
            return userPromptTemplate;
        }
        Matcher matcher = VAR_PATTERN.matcher(userPromptTemplate);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = variables.get(key);
            String replacement = val != null ? Matcher.quoteReplacement(val.toString()) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
