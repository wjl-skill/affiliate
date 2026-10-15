package com.affiliate.platform.domain.quality;

import java.math.BigDecimal;

/**
 * 素材质量评估等级枚举 (Material Quality Level)
 * <p>
 * 遵循严格判定短路顺序：
 * UNRATED -> STAR -> POTENTIAL -> BLACKHOLE -> RISK -> CORE
 */
public enum MaterialQualityLevel {

    /**
     * 未评级：有效样本量不足或未匹配到投放数据，不进入星级评定
     */
    UNRATED("UNRATED", "未评级", BigDecimal.ZERO, "样本量不足，建议继续观察积累数据"),

    /**
     * 明星素材：高回收、低获客成本、且已跑出规模的高价值爆款
     */
    STAR("STAR", "明星素材", BigDecimal.valueOf(95.0), "高回收·低成本·已放量，建议高优先级追加预算并批量衍生变体"),

    /**
     * 潜力素材：高回收，但消耗尚未达标（有扩量空间）或前链路点击率出众
     */
    POTENTIAL("POTENTIAL", "潜力素材", BigDecimal.valueOf(80.0), "高回收·具备放量潜能，建议逐步提升出价与预算进行放量测试"),

    /**
     * 黑洞素材：低回收且高成本，属于严重亏损的劣质资产
     */
    BLACKHOLE("BLACKHOLE", "黑洞素材", BigDecimal.valueOf(15.0), "低回收·高成本·严重失血，建议立即熔断下线并换量"),

    /**
     * 风险素材：回收偏弱，或前链路点击与后链路转化双疲软
     */
    RISK("RISK", "风险素材", BigDecimal.valueOf(35.0), "回收或点击转化偏弱，列入重点监控名单，适度控制消耗"),

    /**
     * 主力素材：样本充足，未命中明星/潜力/黑洞/风险的稳定基本盘物料
     */
    CORE("CORE", "主力素材", BigDecimal.valueOf(60.0), "样本充足·表现平稳，作为广告组日常稳定消耗的基石物料");

    private final String code;
    private final String title;
    private final BigDecimal defaultScore;
    private final String defaultAdvice;

    MaterialQualityLevel(String code, String title, BigDecimal defaultScore, String defaultAdvice) {
        this.code = code;
        this.title = title;
        this.defaultScore = defaultScore;
        this.defaultAdvice = defaultAdvice;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getDefaultScore() {
        return defaultScore;
    }

    public String getDefaultAdvice() {
        return defaultAdvice;
    }

    public static MaterialQualityLevel fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNRATED;
        }
        for (MaterialQualityLevel level : values()) {
            if (level.code.equalsIgnoreCase(code.trim())) {
                return level;
            }
        }
        return UNRATED;
    }
}
