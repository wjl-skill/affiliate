package com.affiliate.platform.domain.ai;

/**
 * AI 生成任务业务类型 (AI Generation Task Type)
 */
public enum AiGenerateType {

    /**
     * 文生视频 (Text-to-Video)
     */
    TEXT_TO_VIDEO("TEXT_TO_VIDEO", "文生视频"),

    /**
     * 图生视频 / 首尾帧生视频 (Image-to-Video)
     */
    IMAGE_TO_VIDEO("IMAGE_TO_VIDEO", "图生视频"),

    /**
     * 多图/角色参考生视频 (Multi-Reference-to-Video)
     */
    REFERENCE_TO_VIDEO("REFERENCE_TO_VIDEO", "参考生视频"),

    /**
     * 视频风格重绘与指令编辑 (Video-to-Video Edit)
     */
    VIDEO_EDIT("VIDEO_EDIT", "视频编辑"),

    /**
     * 数字人驱动 (Digital Human Audio/Video Drive)
     */
    DIGITAL_HUMAN("DIGITAL_HUMAN", "数字人驱动");

    private final String code;
    private final String description;

    AiGenerateType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static AiGenerateType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return TEXT_TO_VIDEO;
        }
        for (AiGenerateType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return TEXT_TO_VIDEO;
    }
}
