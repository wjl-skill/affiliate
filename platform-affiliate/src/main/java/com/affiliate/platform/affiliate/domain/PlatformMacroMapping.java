package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * 第三方平台宏映射 (Platform Macro Mapping Record)
 * <p>
 * 描述某广告/流量平台的宏写法与本平台标准宏的对照，例如 AWIN 的 `{clickid}` 对应本平台 `click_id`。
 *
 * @param id                 映射记录 ID
 * @param platformCode       平台代码 (大写标识，如 AWIN、SHAREASALE、CJ)
 * @param platformName       平台展示名称
 * @param macroKey           本平台标准宏键
 * @param platformMacroToken 该平台实际宏写法 (含其括号风格，如 {clickid}、[ssn]、__CLICKID__)
 * @param remark             备注
 * @param status             状态 (ACTIVE / DISABLED)
 * @param createdAt          创建时间
 * @param updatedAt          最近更新时间
 */
public record PlatformMacroMapping(
        String id,
        @NotBlank String platformCode,
        String platformName,
        @NotBlank String macroKey,
        @NotBlank String platformMacroToken,
        String remark,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public PlatformMacroMapping {
        status = status == null || status.isBlank() ? "ACTIVE" : status;
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }

    public PlatformMacroMapping withId(String newId) {
        return new PlatformMacroMapping(newId, platformCode, platformName, macroKey,
                platformMacroToken, remark, status, createdAt, updatedAt);
    }
}
