package com.affiliate.platform.security;

import com.affiliate.platform.entity.ApiKeyEntity;
import com.affiliate.platform.mapper.ApiKeyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 开放平台 B2B 机器对接 API-Key 与 HMAC 签名鉴权服务 (API-Key Authentication Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `api_key` 表进行凭证管理与防重放验签。
 */
@Service
public class ApiKeyService {

    public record ApiKeyInfo(String keyId, String secret, String tenantId, boolean active) {}

    private final ApiKeyMapper apiKeyMapper;
    private final ConcurrentMap<String, ApiKeyInfo> fallbackRegistry = new ConcurrentHashMap<>();

    public ApiKeyService() {
        this(null);
    }

    @Autowired
    public ApiKeyService(@Autowired(required = false) ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    /**
     * 注册或更新 API Key 凭证至 PostgreSQL
     */
    public void registerKey(ApiKeyInfo keyInfo) {
        if (apiKeyMapper != null) {
            ApiKeyEntity entity = new ApiKeyEntity(
                    keyInfo.keyId(),
                    keyInfo.tenantId(),
                    keyInfo.secret(),
                    keyInfo.active(),
                    Instant.now()
            );
            if (apiKeyMapper.selectById(keyInfo.keyId()) != null) {
                apiKeyMapper.updateById(entity);
            } else {
                apiKeyMapper.insert(entity);
            }
            return;
        }
        fallbackRegistry.put(keyInfo.keyId(), keyInfo);
    }

    /**
     * 验证签名（基于数据库中配置的密钥）
     */
    public boolean verify(String keyId, long timestamp, String payload, String signatureHex) {
        if (keyId == null || signatureHex == null) {
            return false;
        }

        String secret;
        boolean active;

        if (apiKeyMapper != null) {
            ApiKeyEntity entity = apiKeyMapper.selectById(keyId);
            if (entity == null || !Boolean.TRUE.equals(entity.getActive())) {
                return false;
            }
            secret = entity.getSecret();
            active = Boolean.TRUE.equals(entity.getActive());
        } else {
            ApiKeyInfo info = fallbackRegistry.get(keyId);
            if (info == null || !info.active()) {
                return false;
            }
            secret = info.secret();
            active = info.active();
        }

        if (!active) {
            return false;
        }

        // 1. 防重放校验：时间戳漂移不超过 5 分钟 (300 秒)
        long nowSec = Instant.now().getEpochSecond();
        long reqSec = timestamp > 10_000_000_000L ? timestamp / 1000 : timestamp;
        if (Math.abs(nowSec - reqSec) > 300) {
            return false;
        }

        // 2. 计算期望的 HMAC-SHA256 签名
        try {
            String signPayload = reqSec + "\n" + (payload == null ? "" : payload);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(signPayload.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(rawHmac);

            // 常数时间比较防时序侧信道攻击
            return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8), signatureHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 辅助生成合法签名（供客户端或单测使用）
     */
    public String sign(String secret, long timestamp, String payload) {
        try {
            long reqSec = timestamp > 10_000_000_000L ? timestamp / 1000 : timestamp;
            String signPayload = reqSec + "\n" + (payload == null ? "" : payload);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(signPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("sign error", e);
        }
    }
}
