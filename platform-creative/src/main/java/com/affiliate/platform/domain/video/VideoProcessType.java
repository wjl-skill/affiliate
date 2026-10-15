package com.affiliate.platform.domain.video;

/**
 * 视频处理操作类型
 */
public enum VideoProcessType {
    INTRO_STITCH("片头物理拼接与画幅对齐"),
    WATERMARK_REMOVE("去水印虚化擦除"),
    SUBTITLE("智能字幕压制"),
    CTA_OVERLAY("行动呼吁动效贴片"),
    TRIM("分段无损裁剪"),
    BATCH_ASSEMBLE("笛卡尔积批量混剪组合");

    private final String description;

    VideoProcessType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
