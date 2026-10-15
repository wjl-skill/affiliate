package com.affiliate.platform.domain.studio;

/**
 * 剪映草稿工程多轨道类型
 */
public enum CapCutTrackType {
    VIDEO("主视频轨"),
    AUDIO_VOICEOVER("配音旁白轨"),
    AUDIO_BGM("背景音乐轨"),
    SUBTITLE("花字字幕轨");

    private final String description;

    CapCutTrackType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
