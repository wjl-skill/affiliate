package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 多云 S3 对象存储配置实体 (S3 Storage Config Entity)
 * <p>
 * 映射数据库表 `sys_s3_storage_config`。
 */
@TableName("sys_s3_storage_config")
public class S3StorageConfigEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String name;
    private String provider;
    private String region;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String secretAccessKey;
    private String publicCdnUrl;
    private String pathPrefix;
    private Boolean isDefault;
    private String status;
    private Instant createdAt;

    public S3StorageConfigEntity() {}

    public S3StorageConfigEntity(String id, String tenantId, String name, String provider,
                                 String region, String endpoint, String bucketName,
                                 String accessKeyId, String secretAccessKey, String publicCdnUrl,
                                 String pathPrefix, Boolean isDefault, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.provider = provider;
        this.region = region;
        this.endpoint = endpoint;
        this.bucketName = bucketName;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.publicCdnUrl = publicCdnUrl;
        this.pathPrefix = pathPrefix;
        this.isDefault = isDefault;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

    public String getPublicCdnUrl() { return publicCdnUrl; }
    public void setPublicCdnUrl(String publicCdnUrl) { this.publicCdnUrl = publicCdnUrl; }

    public String getPathPrefix() { return pathPrefix; }
    public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean aDefault) { isDefault = aDefault; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
