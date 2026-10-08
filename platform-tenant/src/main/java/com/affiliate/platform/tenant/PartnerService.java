package com.affiliate.platform.tenant;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.repository.Repository;
import com.affiliate.platform.service.NotFoundException;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合作方连接管理服务 (Partner Connection Service)
 * <p>
 * 维护系统与外部 DSP、SSP 及 ADX 节点的端点连接、鉴权密钥与在线同步状态。
 * 生产加固：贯穿租户隔离，并对对外部暴露的 API Key/Token 敏感凭证进行掩码脱敏。
 */
@Service
public class PartnerService {

    // 合作方持久化仓储
    private final Repository<PartnerConnection> repository;

    public PartnerService(Repository<PartnerConnection> repository) {
        this.repository = repository;
    }

    /**
     * 注册接入新的外部合作方连接
     *
     * @param input 合作方信息
     * @return 状态为 ACTIVE 的连接记录
     */
    public PartnerConnection create(PartnerConnection input) {
        String tenant = (input.tenantId() != null && !input.tenantId().isBlank() && !"public".equals(input.tenantId()))
                ? input.tenantId()
                : (TenantContext.get() != null ? TenantContext.get() : "public");

        PartnerConnection saved = repository.save(new PartnerConnection(
                repository.nextId("partner"),
                tenant,
                input.name(),
                input.type(),
                input.endpoint(),
                input.settings(),
                ConnectionStatus.ACTIVE,
                Instant.now()
        ));
        return maskConnection(saved);
    }

    /**
     * 获取全量配置的合作方连接（已进行租户过滤与敏感凭据脱敏）
     *
     * @return 脱敏后的合作方列表
     */
    public List<PartnerConnection> list() {
        return repository.findAll().stream()
                .map(this::maskConnection)
                .toList();
    }

    /**
     * 根据 ID 检索合作方配置（脱敏后返回）
     *
     * @param id 合作方标识
     * @return 合作方实体
     */
    public PartnerConnection get(String id) {
        PartnerConnection conn = repository.find(id)
                .orElseThrow(() -> new NotFoundException("partner", id));
        return maskConnection(conn);
    }

    /**
     * 变更合作方连接状态（如手动切为 PAUSED 或 ERROR）
     *
     * @param id     合作方标识
     * @param status 新状态
     * @return 更新后的连接记录（脱敏）
     */
    public PartnerConnection setStatus(String id, ConnectionStatus status) {
        PartnerConnection raw = repository.find(id)
                .orElseThrow(() -> new NotFoundException("partner", id));
        PartnerConnection updated = repository.save(raw.withStatus(status));
        return maskConnection(updated);
    }

    /**
     * 实体凭据脱敏包装
     */
    private PartnerConnection maskConnection(PartnerConnection conn) {
        if (conn == null) {
            return null;
        }
        return conn.withMaskedSettings(maskSensitiveSettings(conn.settings()));
    }

    /**
     * 对敏感配置（如 apiKey, secret, token, password 等）进行掩码
     */
    public static Map<String, String> maskSensitiveSettings(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (val == null) {
                masked.put(key, null);
                continue;
            }
            String lowerKey = key.toLowerCase();
            if (lowerKey.contains("key") || lowerKey.contains("secret") || lowerKey.contains("token")
                    || lowerKey.contains("password") || lowerKey.contains("credential") || lowerKey.contains("auth")) {
                masked.put(key, maskSecret(val));
            } else {
                masked.put(key, val);
            }
        }
        return Collections.unmodifiableMap(masked);
    }

    private static String maskSecret(String val) {
        if (val.length() <= 6) {
            return "******";
        }
        return val.substring(0, 3) + "******" + val.substring(val.length() - 3);
    }
}
