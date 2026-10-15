package com.affiliate.platform.domain.studio;

/**
 * 剪映草稿工程导出结果
 */
public record CapCutDraftExportResult(
        String projectId,
        int totalScenes,
        int totalDurationSeconds,
        String draftUrl,
        String capcutPackageDownloadUrl
) {}
