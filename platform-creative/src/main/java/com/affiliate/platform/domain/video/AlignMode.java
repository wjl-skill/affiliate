package com.affiliate.platform.domain.video;

/**
 * 画幅对齐模式
 */
public enum AlignMode {
    LETTERBOX("等比缩放并补齐黑边"),
    CROP("居中裁切填满"),
    SCALE("强制非等比拉伸");

    private final String description;

    AlignMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
