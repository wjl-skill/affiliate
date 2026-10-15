package com.affiliate.platform.domain.studio;

import java.time.Instant;
import java.util.UUID;

/**
 * 数字人主播形象资产
 */
public record DigitalHumanAvatar(
        String id,
        String tenantId,
        String avatarName,
        String avatarType,
        String originalImageUrl,
        String maskImageUrl,
        String defaultSpeaker,
        String status,
        Instant createdAt
) {
    public static DigitalHumanAvatar create(
            String tenantId,
            String avatarName,
            String avatarType,
            String originalImageUrl,
            String maskImageUrl,
            String defaultSpeaker
    ) {
        String id = "dha_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new DigitalHumanAvatar(
                id,
                tenantId != null ? tenantId : "public",
                avatarName,
                avatarType != null ? avatarType : "2D_PHOTO",
                originalImageUrl,
                maskImageUrl,
                defaultSpeaker != null ? defaultSpeaker : "zh_female_shuangkuai",
                "ACTIVE",
                Instant.now()
        );
    }
}
