package com.affiliate.platform.domain.studio;

/**
 * 分镜镜头衔接过渡类型
 */
public enum TransitionType {
    CHAIN_EXTEND("首尾边界关键帧连续延展"),
    HARD_CUT("硬切直接切换"),
    MATCH_CUT("图形或动作匹配剪辑"),
    SAME_SCENE("同场景景别推拉切换");

    private final String description;

    TransitionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
