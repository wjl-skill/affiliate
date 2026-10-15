package com.affiliate.platform.domain.studio;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AI 长视频角色与商品资产实体
 */
public record StudioAsset(
        String id,
        String tenantId,
        String projectId,
        StudioAssetType assetType,
        String name,
        String description,
        Map<String, Object> promptData,
        String imageUrl,
        StudioAssetStatus status,
        Instant createdAt
) {
    public static StudioAsset create(
            String tenantId,
            String projectId,
            StudioAssetType assetType,
            String name,
            String description,
            Map<String, Object> promptData
    ) {
        String id = "ast_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new StudioAsset(
                id,
                tenantId != null ? tenantId : "public",
                projectId,
                assetType != null ? assetType : StudioAssetType.CHARACTER,
                name != null ? name : "Asset",
                description != null ? description : "",
                promptData != null ? Map.copyOf(promptData) : Map.of(),
                null,
                StudioAssetStatus.DRAFT,
                Instant.now()
        );
    }

    public StudioAsset withGeneratedImage(String imageUrl) {
        return new StudioAsset(
                id, tenantId, projectId, assetType, name, description,
                promptData, imageUrl, StudioAssetStatus.GENERATED, createdAt
        );
    }

    public StudioAsset withStatus(StudioAssetStatus newStatus) {
        return new StudioAsset(
                id, tenantId, projectId, assetType, name, description,
                promptData, imageUrl, newStatus, createdAt
        );
    }
}
