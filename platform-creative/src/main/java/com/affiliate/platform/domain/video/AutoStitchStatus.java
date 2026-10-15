package com.affiliate.platform.domain.video;

/**
 * 批量混剪定时任务状态
 */
public enum AutoStitchStatus {
    PENDING("等待执行"),
    EXECUTED("已执行"),
    CANCELLED("已取消");

    private final String description;

    AutoStitchStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
