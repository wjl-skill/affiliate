package com.affiliate.platform.domain.studio;

/**
 * 6 套行业分镜提示词管线类型
 */
public enum AdTypePipeline {
    ECOMMERCE_SHOWCASE("电商口播带货管线", "电商带货、爆款单品介绍、成分特写与功效演示"),
    GAMING_INTERACTIVE("IAA 小游戏互动试玩管线", "小游戏吸量试玩、失误死局操作、诱导玩家点击自救"),
    DRAMA_HOOK_TWIST("短剧痛点反转管线", "短剧高能卡点、家庭/职场矛盾、痛点抛出与神级反转"),
    BRAND_CINEMATIC("品牌形象故事管线", "品牌出海宣传、电影级调色景别、情感共鸣与愿景传递"),
    FLASH_SALE_PROMO("促销强刺激管线", "大促秒杀、限时骨折补贴、库存倒计时急迫感逼单"),
    LOCAL_LIFESTYLE("本地生活探店测评管线", "餐饮/酒旅本地打卡、第一视角沉浸体验、闭眼入实惠套餐");

    private final String displayName;
    private final String description;

    AdTypePipeline(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static AdTypePipeline fromAdType(String adType) {
        if (adType == null || adType.isBlank()) {
            return ECOMMERCE_SHOWCASE;
        }
        String lower = adType.toLowerCase();
        if (lower.contains("game") || lower.contains("gaming") || lower.contains("iaa")) {
            return GAMING_INTERACTIVE;
        }
        if (lower.contains("drama") || lower.contains("novel") || lower.contains("twist") || lower.contains("短剧")) {
            return DRAMA_HOOK_TWIST;
        }
        if (lower.contains("brand") || lower.contains("cinematic") || lower.contains("品牌")) {
            return BRAND_CINEMATIC;
        }
        if (lower.contains("sale") || lower.contains("flash") || lower.contains("promo") || lower.contains("促销")) {
            return FLASH_SALE_PROMO;
        }
        if (lower.contains("local") || lower.contains("life") || lower.contains("food") || lower.contains("探店")) {
            return LOCAL_LIFESTYLE;
        }
        return ECOMMERCE_SHOWCASE;
    }
}
