package com.affiliate.platform.domain.video;

/**
 * 视频处理任务生命周期状态
 */
public enum VideoProcessStatus {
    PENDING("排队中"),
    PROCESSING("处理中"),
    SUCCESS("处理成功"),
    FAILED("处理失败");

    private final String description;

    VideoProcessStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
