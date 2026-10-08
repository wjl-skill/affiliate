package com.affiliate.platform.domain;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * 外部生态合作方集成连接记录实体 (Partner Connection Record)
 * <p>
 * 存储与第三方 ADX、DSP、SSP 或 Google 广告生态的通信接入点、鉴权参数与同步状态。
 *
 * @param id        连接唯一主键 ID
 * @param name      连接配置名称
 * @param type      对接供给类型 (DSP, SSP, ADX)
 * @param endpoint  远端目标服务器 API 接入端点 URL
 * @param settings  个性化配置参数键值对（如 API Key, OAuth 凭据, 追踪宏）
 * @param status    当前连接状态（ACTIVE 正常、PAUSED 暂停、ERROR 故障）
 * @param updatedAt 最后配置更新时间戳
 */
public record PartnerConnection(
        String id,
        String tenantId,
        @NotBlank String name,
        @NotNull SupplyType type,
        @NotBlank String endpoint,
        Map<String, String> settings,
        ConnectionStatus status,
        Instant updatedAt
) {
    /**
     * 紧凑构造器 - 租户缺省保护与配置字典防御性不可变拷贝
     */
    public PartnerConnection {
        tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    /**
     * 兼容性构造器（默认 public 租户）
     */
    public PartnerConnection(
            String id,
            String name,
            SupplyType type,
            String endpoint,
            Map<String, String> settings,
            ConnectionStatus status,
            Instant updatedAt
    ) {
        this(id, "public", name, type, endpoint, settings, status, updatedAt);
    }

    /**
     * 变更连接状态并返回新不可变记录
     *
     * @param next 新的连接运行状态
     * @return 状态变更后的实体对象
     */
    public PartnerConnection withStatus(ConnectionStatus next) {
        return new PartnerConnection(id, tenantId, name, type, endpoint, settings, next, Instant.now());
    }

    public PartnerConnection withTenant(String nextTenant) {
        return new PartnerConnection(id, nextTenant, name, type, endpoint, settings, status, updatedAt);
    }

    public PartnerConnection withMaskedSettings(Map<String, String> masked) {
        return new PartnerConnection(id, tenantId, name, type, endpoint, masked, status, updatedAt);
    }
}
