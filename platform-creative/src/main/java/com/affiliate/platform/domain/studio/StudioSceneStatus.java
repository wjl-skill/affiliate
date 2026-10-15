package com.affiliate.platform.domain.studio;

/**
 * 分镜镜头生成状态
 */
public enum StudioSceneStatus {
    DRAFT("分镜草稿"),
    GENERATING("视频片段生成中"),
    GENERATED("视频已生成完毕"),
    FAILED("片段生成失败");

    private final String description;

    StudioSceneStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
