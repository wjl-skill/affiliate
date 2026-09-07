package com.affiliate.platform.domain;

import com.affiliate.platform.service.DomainValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * 媒体广告位领域实体 (Ad Slot Domain Record)
 * <p>
 * 代表发布商（Publisher）在网站或移动 App 中划分的广告展示版位。
 * 具备版位尺寸、竞价底价、HTTPS 安全要求与启停状态约束。
 *
 * @param id         广告位唯一标识符
 * @param name       广告位名称（例如 "首页顶部横幅 728x90"）
 * @param width      广告位像素宽度（必须 > 0）
 * @param height     广告位像素高度（必须 > 0）
 * @param floorPrice 媒体设定的竞价保底价格（单位：USD，不可为负数）
 * @param secure     是否强制要求物料及链路支持 HTTPS 安全加密
 * @param active     广告位是否处于开启接单状态
 * @param createdAt  广告位创建时间
 */
public record AdSlot(
        String id,
        @NotBlank String name,
        @Positive int width,
        @Positive int height,
        @Positive double floorPrice,
        boolean secure,
        boolean active,
        Instant createdAt
) {
    /**
     * 紧凑构造器 - 校验尺寸与底价合法性
     */
    public AdSlot {
        // 校验广告位宽度必须大于 0
        if (width <= 0) throw new DomainValidationException("width", "must be positive");
        // 校验广告位高度必须大于 0
        if (height <= 0) throw new DomainValidationException("height", "must be positive");
        // 校验底价不可为负数
        if (floorPrice < 0) throw new DomainValidationException("floorPrice", "cannot be negative");
    }

    /**
     * 切换广告位的启用状态并返回新的不可变实体
     *
     * @param v 是否激活
     * @return 更新激活状态后的 AdSlot 实例
     */
    public AdSlot activate(boolean v) {
        return new AdSlot(id, name, width, height, floorPrice, secure, v, createdAt);
    }
}
