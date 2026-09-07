package com.affiliate.platform.dmp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

/**
 * 数据管理平台受众分群领域实体 (DMP Audience Segment Record)
 * <p>
 * 存储面向程序化广告投放的匿名、短时效受众客群（基于 Cookie ID、设备 IDFA/GAID 等），
 * 具备客群来源、分类标签、到期时间与有效性判定机制。
 *
 * @param id          受众分群全局唯一主键 ID
 * @param name        受众分群名称（例如 "近30天高价值数码潜客"）
 * @param source      受众数据来源（THIRD_PARTY 三方数据市场, SECOND_PARTY 媒体合作置换, LOOKALIKE 算法拓客）
 * @param taxonomy    分群标签层级（例如 "technology/hardware", "finance/investment"）
 * @param expiresAt   分群生命周期到期时间戳（到期后自动失效不参与投放匹配）
 * @param memberCount 当前分群内包含的匿名受众成员总数
 * @param status      分群状态（DRAFT 草稿, ACTIVE 启用中, ARCHIVED 已归档）
 * @param createdAt   分群创建时间戳
 */
public record AudienceSegment(
        String id,
        @NotBlank String name,
        @NotNull Source source,
        Set<String> taxonomy,
        Instant expiresAt,
        long memberCount,
        Status status,
        Instant createdAt
) {
    /**
     * 紧凑构造器 - 标签分类集合防御性不可变拷贝
     */
    public AudienceSegment {
        taxonomy = taxonomy == null ? Set.of() : Set.copyOf(taxonomy);
    }

    /**
     * 受众数据来源渠道
     */
    public enum Source {
        /** 第三方数据服务商采购 (Third Party Data) */
        THIRD_PARTY,
        /** 第二方媒体战略置换 (Second Party Data) */
        SECOND_PARTY,
        /** 基于种子人群的算法相似放大扩量 (Lookalike Modeling) */
        LOOKALIKE
    }

    /**
     * 受众分群生命周期状态
     */
    public enum Status {
        /** 草稿：尚未生效 */
        DRAFT,
        /** 启用中：可被 DSP/ADX 关联定向投放 */
        ACTIVE,
        /** 已归档：已废弃或下线 */
        ARCHIVED
    }

    /**
     * 切换分群的激活状态
     *
     * @param active true 为 ACTIVE，false 为 DRAFT
     * @return 状态变更后的不可变分群实体
     */
    public AudienceSegment activate(boolean active) {
        return new AudienceSegment(id, name, source, taxonomy, expiresAt, memberCount,
                active ? Status.ACTIVE : Status.DRAFT, createdAt);
    }

    /**
     * 判断当前时间受众分群是否仍处于合法有效周期内
     *
     * @param now 当前系统时间戳
     * @return true 代表处于 ACTIVE 状态且尚未到期
     */
    public boolean validAt(Instant now) {
        return status == Status.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }
}
