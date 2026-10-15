package com.affiliate.platform.domain.studio;

/**
 * AI 长视频项目状态
 */
public enum StudioProjectStatus {
    DRAFT("草稿初始"),
    SCRIPT_READY("分镜剧本已就绪"),
    GENERATING("视频片段批量生成中"),
    COMPLETED("成片与草稿合成完毕"),
    FAILED("流水线处理失败");

    private final String description;

    StudioProjectStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
