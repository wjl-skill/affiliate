package com.affiliate.platform.domain.studio;

/**
 * 长视频资产类型 (角色、产品商品、场景设定)
 */
public enum StudioAssetType {
    CHARACTER("角色人物"),
    PRODUCT("核心商品"),
    SCENE_SETTING("场景设定");

    private final String description;

    StudioAssetType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
