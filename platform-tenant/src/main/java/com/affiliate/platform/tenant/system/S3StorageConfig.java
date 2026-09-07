package com.affiliate.platform.tenant.system;

import java.time.Instant;

/**
 * S3 / 多云对象存储凭据与 CDN 配置 (S3 Storage Configuration Entity)
 */
public record S3StorageConfig(
        String id,
        String tenantId,
        String name,
        Provider provider,
        String region,
        String endpoint,
        String bucketName,
        String accessKeyId,
        String secretAccessKey,
        String publicCdnUrl,
        String pathPrefix,
        boolean isDefault,
        Status status,
        Instant createdAt
) {
    public enum Provider {
        AWS_S3,
        CLOUDFLARE_R2,
        MINIO,
        ALIYUN_OSS,
        TENCENT_COS
    }

    public enum Status {
        ACTIVE,
        DISABLED
    }

    /**
     * 密文安全脱敏输出 (如: AKIA****9876)
     */
    public S3StorageConfig toMasked() {
        String maskedKey = (secretAccessKey != null && secretAccessKey.length() > 6)
                ? secretAccessKey.substring(0, 3) + "********" + secretAccessKey.substring(secretAccessKey.length() - 3)
                : "********";
        return new S3StorageConfig(id, tenantId, name, provider, region, endpoint, bucketName, accessKeyId, maskedKey, publicCdnUrl, pathPrefix, isDefault, status, createdAt);
    }

    public S3StorageConfig withDefault(boolean def) {
        return new S3StorageConfig(id, tenantId, name, provider, region, endpoint, bucketName, accessKeyId, secretAccessKey, publicCdnUrl, pathPrefix, def, status, createdAt);
    }
}
