package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * 标准追踪宏参数字典 (Standard Macro Parameter Dictionary Record)
 * <p>
 * 定义本平台追踪/回传链接中的标准宏占位符，如 `{click_id}`、`{sub1}`。
 *
 * @param id          宏字典 ID
 * @param macroKey    标准宏键 (不带花括号，如 click_id)
 * @param displayName 宏中文名称
 * @param description 宏用途说明
 * @param sampleValue 示例取值 (用于链接渲染预览)
 * @param category    分类 (ATTRIBUTION 归因 / SUB_TRACKING 子渠道 / TRANSACTION 交易 / ENVIRONMENT 环境)
 * @param status      状态 (ACTIVE / DISABLED)
 * @param createdAt   创建时间
 */
public record MacroParam(
        String id,
        @NotBlank String macroKey,
        String displayName,
        String description,
        String sampleValue,
        String category,
        String status,
        Instant createdAt
) {
    public MacroParam {
        category = category == null || category.isBlank() ? "ATTRIBUTION" : category;
        status = status == null || status.isBlank() ? "ACTIVE" : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public MacroParam withId(String newId) {
        return new MacroParam(newId, macroKey, displayName, description, sampleValue, category, status, createdAt);
    }
}
