package com.affiliate.platform.domain.ai;

/**
 * 动态表单字段可选值 (Field Option)
 */
public record AiVideoModelFieldOptionDTO(
        String label,
        Object value,
        boolean isDefault
) {
    public static AiVideoModelFieldOptionDTO of(String label, Object value) {
        return new AiVideoModelFieldOptionDTO(label, value, false);
    }

    public static AiVideoModelFieldOptionDTO ofDefault(String label, Object value) {
        return new AiVideoModelFieldOptionDTO(label, value, true);
    }
}
