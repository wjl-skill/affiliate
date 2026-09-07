package com.affiliate.platform.dmp;

import java.util.List;
import java.util.Map;

/**
 * 规则型动态受众计算引擎 (DMP Dynamic Segmentation Engine)
 * <p>
 * 支持基于用户行为属性、偏好标签与发生频次的动态规则判定：
 * 规则结构：属性键值操作符（EQUALS, CONTAINS, GREATER_EQUAL）与频次门槛（minCount）。
 */
public class DynamicSegmentEngine {

    public enum Operator {
        /** 精确相等 */
        EQUALS,
        /** 字符串/集合包含 */
        CONTAINS,
        /** 数值大于或等于 */
        GREATER_EQUAL
    }

    public record Rule(String attributeKey, Operator operator, Object targetValue, Integer minCount) {}

    /**
     * 判定用户属性画像是否满足规则集（AND 逻辑，所有规则全部满足）
     *
     * @param userAttributes 用户画像属性字典（如 {"category": "sports", "visit_count": 5}）
     * @param rules          分群规则列表
     * @return true 代表命中受众规则
     */
    public boolean evaluate(Map<String, Object> userAttributes, List<Rule> rules) {
        if (userAttributes == null || rules == null || rules.isEmpty()) {
            return false;
        }

        for (Rule rule : rules) {
            Object actual = userAttributes.get(rule.attributeKey());
            if (actual == null) {
                return false;
            }

            switch (rule.operator()) {
                case EQUALS -> {
                    if (!actual.toString().equalsIgnoreCase(rule.targetValue().toString())) {
                        return false;
                    }
                }
                case CONTAINS -> {
                    if (!actual.toString().toLowerCase().contains(rule.targetValue().toString().toLowerCase())) {
                        return false;
                    }
                }
                case GREATER_EQUAL -> {
                    double actualNum = toDouble(actual);
                    double targetNum = toDouble(rule.targetValue());
                    if (actualNum < targetNum) {
                        return false;
                    }
                }
            }

            // 频次门槛判定
            if (rule.minCount() != null && rule.minCount() > 0) {
                Object countObj = userAttributes.get(rule.attributeKey() + "_count");
                if (countObj == null || toDouble(countObj) < rule.minCount()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
