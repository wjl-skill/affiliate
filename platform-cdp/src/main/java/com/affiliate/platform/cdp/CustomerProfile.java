package com.affiliate.platform.cdp;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 客户数据平台一方画像实体 (CDP Customer Profile Record)
 * <p>
 * 存储以企业一方唯一客户 ID 为核心的持久化画像资产：
 * 包含跨端多源身份标识 (ID Graph)、用户属性字典 (Attributes) 及标签偏好特征 (Traits)。
 *
 * @param id          CDP 统一档案唯一主键 ID
 * @param primaryId   一方系统主客户标识（例如 "cust_1001", "crm_user_888"）
 * @param identifiers 跨渠道关联的身份图谱标识集（如 email, phone, openid, device_id）
 * @param attributes  客户基础事实属性字典（如 {"tier": "VIP", "country": "US"}）
 * @param traits      客户标签与行为偏好集合（如 {"high_spender", "sports_lover"}）
 * @param lastSeenAt  该客户最近活跃时间戳
 * @param status      客户档案合规状态（ACTIVE 正常, OPTED_OUT 隐私退出拒绝定向, DELETED 被遗忘已擦除）
 * @param createdAt   客户档案首次建立时间戳
 */
public record CustomerProfile(
        String id,
        @NotBlank String primaryId,
        Set<String> identifiers,
        Map<String, String> attributes,
        Set<String> traits,
        Instant lastSeenAt,
        Status status,
        Instant createdAt
) {
    /**
     * 紧凑构造器 - 集合防御性不可变拷贝
     */
    public CustomerProfile {
        identifiers = identifiers == null ? Set.of() : Set.copyOf(identifiers);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        traits = traits == null ? Set.of() : Set.copyOf(traits);
    }

    /**
     * 客户档案合规生命周期状态
     */
    public enum Status {
        /** 正常：可用于一方受众分析与归因 */
        ACTIVE,
        /** 退出：已行使隐私选择权（Opt-out），禁止用于广告营销 */
        OPTED_OUT,
        /** 已删除：已执行 GDPR/CCPA 被遗忘权物理擦除 */
        DELETED
    }

    /**
     * 确定性身份打通与增量合并 (ID Stitching & Incremental Merge)
     *
     * @param ids        新触点捕获的身份标识列表
     * @param attrs      新触点增量属性键值
     * @param nextTraits 新增标签特征
     * @param seenAt     本次触点发生时间
     * @return 合并归一后的新 CustomerProfile 不可变实例
     */
    public CustomerProfile merge(Set<String> ids, Map<String, String> attrs, Set<String> nextTraits, Instant seenAt) {
        // 1. 合并跨触点身份标识集合
        Set<String> mergedIds = new HashSet<>(identifiers);
        if (ids != null) mergedIds.addAll(ids);

        // 2. 合并客户属性字典（以新属性覆盖或增量追加）
        Map<String, String> mergedAttrs = new HashMap<>(attributes);
        if (attrs != null) mergedAttrs.putAll(attrs);

        // 3. 合并客户标签偏好
        Set<String> mergedTraits = new HashSet<>(traits);
        if (nextTraits != null) mergedTraits.addAll(nextTraits);

        return new CustomerProfile(
                id,
                primaryId,
                mergedIds,
                mergedAttrs,
                mergedTraits,
                seenAt == null ? lastSeenAt : seenAt,
                status,
                createdAt
        );
    }
}
