package com.affiliate.platform.domain.ai;

import java.util.List;

/**
 * 动态模型参数字段定义 (Field Meta Definition)
 */
public record AiVideoModelFieldDTO(
        String fieldName,
        String label,
        String fieldType, // "select", "text", "number", "slider", "switch"
        boolean required,
        Object defaultValue,
        String description,
        List<AiVideoModelFieldOptionDTO> options
) {
    public AiVideoModelFieldDTO {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
