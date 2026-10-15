package com.affiliate.platform.domain.studio;

/**
 * 长视频资产状态
 */
public enum StudioAssetStatus {
    DRAFT("提示词草稿"),
    GENERATED("图片已渲染生成"),
    CONFIRMED("人工已确认");

    private final String description;

    StudioAssetStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
