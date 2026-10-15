package com.affiliate.platform.domain.quality;

/**
 * 素材有效样本状态枚举 (Material Sample Status)
 */
public enum MaterialSampleStatus {

    /**
     * 样本充足：同时满足消耗、点击与转化三大保护门槛
     */
    SUFFICIENT("SUFFICIENT", "样本充足"),

    /**
     * 样本不足：消耗、点击或转化未达到最小门槛
     */
    INSUFFICIENT("INSUFFICIENT", "样本不足"),

    /**
     * 未匹配：未在投放事实表中匹配到该素材的有效投放数据
     */
    UNMATCHED("UNMATCHED", "未匹配到广告数据");

    private final String code;
    private final String description;

    MaterialSampleStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
