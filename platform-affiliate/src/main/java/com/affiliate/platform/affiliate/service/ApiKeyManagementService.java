package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.ApiKeyEntity;
import com.affiliate.platform.affiliate.repository.ApiKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API 密钥管理服务（重构版 - 接入 PostgreSQL + 多级缓存）
 */
@Service
public class ApiKeyManagementService {

    private final ApiKeyRepository apiKeyRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String API_KEY_PREFIX = "ak_live_";
    private static final String TEST_KEY_PREFIX = "ak_test_";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public ApiKeyManagementService(
            ApiKeyRepository apiKeyRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建 API Key
     */
    @Transactional
    public ApiKey createApiKey(
            String affiliateId,
            String name,
            Set<ApiScope> scopes,
            ApiKeyEnvironment environment,
            Instant expiresAt
    ) {
        String keyId = generateKeyId();
        String secretKey = generateSecretKey(environment);

        ApiKeyEntity entity = new ApiKeyEntity(
                keyId,
                affiliateId,
                name,
                secretKey,
                serializeScopes(scopes),
                environment.name(),
                ApiKeyStatus.ACTIVE.name(),
                expiresAt,
                null,
                0L,
                Instant.now(),
                null,
                null
        );

        apiKeyRepository.save(entity);

        // 失效相关缓存
        cacheManager.evictByPattern(keyGenerator.patternByPrefix("api_key", "affiliate", affiliateId));

        return toApiKey(entity);
    }

    /**
     * 验证 API Key（高频操作，使用多级缓存）
     */
    public ApiKeyValidationResult validateApiKey(
            String apiKey,
            ApiScope requiredScope,
            String ipAddress
    ) {
        // 从缓存键查找
        String cacheKey = keyGenerator.apiKeyValidation(apiKey);

        // 先尝试从 L1 快速查询（验证场景对延迟敏感）
        Optional<ApiKeyValidationResult> cached = cacheManager.getFromL1Only(
                cacheKey,
                ApiKeyValidationResult.class
        );

        if (cached.isPresent()) {
            return cached.get();
        }

        // L2 和数据库查询
        Optional<ApiKeyEntity> entityOpt = apiKeyRepository.findBySecretKey(apiKey);

        if (entityOpt.isEmpty()) {
            ApiKeyValidationResult result = new ApiKeyValidationResult(false, null, "API key not found");
            // 缓存失败结果（防止频繁查询不存在的 key）
            cacheManager.put(cacheKey, result, Duration.ofMinutes(5));
            return result;
        }

        ApiKeyEntity entity = entityOpt.get();
        ApiKey key = toApiKey(entity);

        // 检查状态
        if (!entity.getStatus().equals(ApiKeyStatus.ACTIVE.name())) {
            return new ApiKeyValidationResult(false, null, "API key is " + entity.getStatus().toLowerCase());
        }

        // 检查过期
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
            revokeApiKey(entity.getId(), "Expired");
            return new ApiKeyValidationResult(false, null, "API key has expired");
        }

        // 检查权限
        Set<ApiScope> scopes = deserializeScopes(entity.getScopes());
        if (requiredScope != null && !scopes.contains(requiredScope) && !scopes.contains(ApiScope.FULL_ACCESS)) {
            return new ApiKeyValidationResult(false, key, "Insufficient permissions");
        }

        // TODO: 检查 IP 白名单和速率限制

        // 更新使用计数（异步）
        updateUsageAsync(entity.getId());

        ApiKeyValidationResult result = new ApiKeyValidationResult(true, key, null);

        // 缓存验证结果
        cacheManager.put(cacheKey, result, Duration.ofMinutes(10));

        return result;
    }

    /**
     * 撤销 API Key
     */
    @Transactional
    public void revokeApiKey(String keyId, String reason) {
        ApiKeyEntity entity = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        entity.setStatus(ApiKeyStatus.REVOKED.name());
        entity.setRevokedReason(reason);
        entity.setRevokedAt(Instant.now());

        apiKeyRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.apiKeyById(keyId));
        cacheManager.evict(keyGenerator.apiKeyValidation(entity.getSecretKey()));
        cacheManager.evictByPattern(keyGenerator.patternByPrefix("api_key", "affiliate", entity.getAffiliateId()));
    }

    /**
     * 轮换 API Key
     */
    @Transactional
    public ApiKeyRotationResult rotateApiKey(String keyId, int gracePeriodDays) {
        ApiKeyEntity oldEntity = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        // 创建新密钥
        Set<ApiScope> scopes = deserializeScopes(oldEntity.getScopes());
        ApiKey newKey = createApiKey(
                oldEntity.getAffiliateId(),
                oldEntity.getName() + " (Rotated)",
                scopes,
                ApiKeyEnvironment.valueOf(oldEntity.getEnvironment()),
                oldEntity.getExpiresAt()
        );

        // 旧密钥设置宽限期
        Instant gracePeriodEnd = Instant.now().plusSeconds(gracePeriodDays * 86400L);
        oldEntity.setStatus(ApiKeyStatus.DEPRECATED.name());
        oldEntity.setExpiresAt(gracePeriodEnd);
        oldEntity.setRevokedReason("Rotated to " + newKey.id());

        apiKeyRepository.save(oldEntity);

        // 失效缓存
        cacheManager.evictByPattern(keyGenerator.patternByPrefix("api_key", "affiliate", oldEntity.getAffiliateId()));

        return new ApiKeyRotationResult(
                toApiKey(oldEntity),
                newKey,
                gracePeriodEnd,
                "Old key will be revoked after " + gracePeriodDays + " days"
        );
    }

    /**
     * 获取渠道的所有 API Keys
     */
    public List<ApiKey> getApiKeys(String affiliateId, ApiKeyStatus status) {
        String cacheKey = keyGenerator.apiKeysByAffiliate(affiliateId);

        return cacheManager.get(
                cacheKey,
                List.class,
                CACHE_TTL,
                () -> {
                    List<ApiKeyEntity> entities = status == null ?
                            apiKeyRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId) :
                            apiKeyRepository.findByAffiliateIdAndStatus(affiliateId, status.name());

                    return entities.stream()
                            .map(this::toApiKey)
                            .collect(Collectors.toList());
                }
        ).orElse(List.of());
    }

    /**
     * 获取即将过期的 API Keys
     */
    public List<ApiKey> getExpiringKeys(int daysBeforeExpiration) {
        Instant threshold = Instant.now().plusSeconds(daysBeforeExpiration * 86400L);

        return apiKeyRepository.findExpiringKeys(threshold).stream()
                .map(this::toApiKey)
                .sorted(Comparator.comparing(ApiKey::expiresAt))
                .collect(Collectors.toList());
    }

    /**
     * 批量撤销过期密钥
     */
    @Transactional
    public int revokeExpiredKeys() {
        List<ApiKeyEntity> expiredKeys = apiKeyRepository.findExpiredKeys(Instant.now());

        for (ApiKeyEntity key : expiredKeys) {
            key.setStatus(ApiKeyStatus.REVOKED.name());
            key.setRevokedReason("Expired");
            key.setRevokedAt(Instant.now());
        }

        apiKeyRepository.saveAll(expiredKeys);

        // 失效缓存
        expiredKeys.forEach(key -> {
            cacheManager.evict(keyGenerator.apiKeyById(key.getId()));
            cacheManager.evict(keyGenerator.apiKeyValidation(key.getSecretKey()));
        });

        return expiredKeys.size();
    }

    /**
     * 生成 Webhook 签名
     */
    public String generateWebhookSignature(String payload, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload + secret).getBytes());
            return "sha256=" + bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 验证 Webhook 签名
     */
    public boolean verifyWebhookSignature(String payload, String signature, String secret) {
        String expectedSignature = generateWebhookSignature(payload, secret);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(),
                signature.getBytes()
        );
    }

    // ========== 私有辅助方法 ==========

    private void updateUsageAsync(String keyId) {
        // 异步更新使用计数（不影响验证性能）
        // TODO: 使用消息队列或异步任务
        apiKeyRepository.findById(keyId).ifPresent(entity -> {
            entity.setUsageCount(entity.getUsageCount() + 1);
            entity.setLastUsedAt(Instant.now());
            apiKeyRepository.save(entity);
        });
    }

    private String generateKeyId() {
        return "key_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateSecretKey(ApiKeyEnvironment environment) {
        String prefix = environment == ApiKeyEnvironment.LIVE ? API_KEY_PREFIX : TEST_KEY_PREFIX;
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return prefix + bytesToHex(randomBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String serializeScopes(Set<ApiScope> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize scopes", e);
        }
    }

    private Set<ApiScope> deserializeScopes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Set<ApiScope>>() {});
        } catch (JsonProcessingException e) {
            return Set.of();
        }
    }

    private ApiKey toApiKey(ApiKeyEntity entity) {
        return new ApiKey(
                entity.getId(),
                entity.getAffiliateId(),
                entity.getName(),
                entity.getSecretKey(),
                deserializeScopes(entity.getScopes()),
                ApiKeyEnvironment.valueOf(entity.getEnvironment()),
                ApiKeyStatus.valueOf(entity.getStatus()),
                entity.getExpiresAt(),
                entity.getRevokedReason(),
                entity.getUsageCount(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getRevokedAt()
        );
    }

    // ========== 数据记录 ==========

    public record ApiKey(
            String id,
            String affiliateId,
            String name,
            String secretKey,
            Set<ApiScope> scopes,
            ApiKeyEnvironment environment,
            ApiKeyStatus status,
            Instant expiresAt,
            String revokedReason,
            long usageCount,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt
    ) {}

    public record ApiKeyValidationResult(
            boolean valid,
            ApiKey apiKey,
            String errorMessage
    ) {}

    public record ApiKeyRotationResult(
            ApiKey oldKey,
            ApiKey newKey,
            Instant gracePeriodEnd,
            String message
    ) {}

    public enum ApiScope {
        READ_OFFERS,
        READ_CONVERSIONS,
        READ_REPORTS,
        READ_CREATIVES,
        WRITE_CONVERSIONS,
        WRITE_POSTBACK,
        WRITE_CREATIVES,
        MANAGE_API_KEYS,
        MANAGE_WEBHOOKS,
        MANAGE_PAYMENT_METHODS,
        FULL_ACCESS
    }

    public enum ApiKeyEnvironment {
        LIVE,
        TEST
    }

    public enum ApiKeyStatus {
        ACTIVE,
        DEPRECATED,
        REVOKED,
        SUSPENDED
    }
}
