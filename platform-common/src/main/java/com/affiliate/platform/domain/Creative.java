package com.affiliate.platform.domain;

import com.affiliate.platform.domain.Enums.AuditStatus;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.service.DomainValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 广告素材领域实体 (Creative Domain Record)
 * <p>
 * 代表广告主投放的图片、视频、原生或 HTML5 物料。
 * 具备强类型业务不变式约束、审核生命周期状态机及第三方曝光/点击监测能力。
 *
 * @param id                 素材全局唯一标识符
 * @param name               素材展示名称
 * @param type               物料类型（横幅 Banner、视频 Video、原生 Native、交互式 HTML5）
 * @param assetUrl           静态物料在 CDN 或对象存储中的绝对访问地址
 * @param landingUrl         用户点击后跳转的目标落地页 (Landing Page URL)
 * @param width              素材宽度像素（必须 > 0）
 * @param height             素材高度像素（必须 > 0）
 * @param categories         IAB 行业类目标签集合（如 "IAB1", "tech"）
 * @param active             素材是否处于激活投放状态
 * @param auditStatus        合规审核状态 (PENDING_REVIEW, APPROVED, REJECTED)
 * @param rejectionReason    审核驳回原因（驳回时必填）
 * @param impressionTrackers 第三方曝光监测像素代码 URL 列表
 * @param clickTrackers      第三方点击监测追踪代码 URL 列表
 * @param createdAt          素材创建时间戳
 */
public record Creative(
        String id,
        @NotBlank String name,
        @NotNull CreativeType type,
        @NotBlank String assetUrl,
        @NotBlank String landingUrl,
        @Positive int width,
        @Positive int height,
        Set<String> categories,
        boolean active,
        AuditStatus auditStatus,
        String rejectionReason,
        List<String> impressionTrackers,
        List<String> clickTrackers,
        Instant createdAt
) {
    /**
     * 紧凑构造器 (Compact Constructor) - 执行业务不变式强校验与防御性浅拷贝
     */
    public Creative {
        if (width <= 0) throw new DomainValidationException("width", "must be positive");
        if (height <= 0) throw new DomainValidationException("height", "must be positive");
        if (assetUrl != null && assetUrl.isBlank()) throw new DomainValidationException("assetUrl", "must not be blank");
        if (landingUrl != null && landingUrl.isBlank()) throw new DomainValidationException("landingUrl", "must not be blank");

        categories = categories == null ? Set.of() : Set.copyOf(categories);
        auditStatus = auditStatus == null ? AuditStatus.APPROVED : auditStatus;
        impressionTrackers = impressionTrackers == null ? List.of() : List.copyOf(impressionTrackers);
        clickTrackers = clickTrackers == null ? List.of() : List.copyOf(clickTrackers);
    }

    /**
     * 兼容性构造器（默认已审核、无驳回原因、无第三方监测代码）
     */
    public Creative(
            String id,
            String name,
            CreativeType type,
            String assetUrl,
            String landingUrl,
            int width,
            int height,
            Set<String> categories,
            boolean active,
            Instant createdAt
    ) {
        this(id, name, type, assetUrl, landingUrl, width, height, categories, active, AuditStatus.APPROVED, null, List.of(), List.of(), createdAt);
    }

    /**
     * 切换素材的投放激活状态
     */
    public Creative activate(boolean v) {
        return new Creative(id, name, type, assetUrl, landingUrl, width, height, categories, v,
                auditStatus, rejectionReason, impressionTrackers, clickTrackers, createdAt);
    }

    /**
     * 审核通过
     */
    public Creative approve() {
        return new Creative(id, name, type, assetUrl, landingUrl, width, height, categories, active,
                AuditStatus.APPROVED, null, impressionTrackers, clickTrackers, createdAt);
    }

    /**
     * 审核驳回
     */
    public Creative reject(String reason) {
        return new Creative(id, name, type, assetUrl, landingUrl, width, height, categories, false,
                AuditStatus.REJECTED, reason, impressionTrackers, clickTrackers, createdAt);
    }

    /**
     * 配置第三方监测代码
     */
    public Creative withTrackers(List<String> imps, List<String> clks) {
        return new Creative(id, name, type, assetUrl, landingUrl, width, height, categories, active,
                auditStatus, rejectionReason, imps, clks, createdAt);
    }

    /**
     * 是否允许参与实时竞价撮合（必须已激活且审核通过）
     */
    public boolean isEligibleForBidding() {
        return active && auditStatus == AuditStatus.APPROVED;
    }
}
