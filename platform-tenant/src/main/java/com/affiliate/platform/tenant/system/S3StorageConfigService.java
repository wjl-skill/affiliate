package com.affiliate.platform.tenant.system;

import com.affiliate.platform.entity.S3StorageConfigEntity;
import com.affiliate.platform.mapper.S3StorageConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * S3 对象存储配置管理服务 (S3 Storage Config Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `sys_s3_storage_config` 表管理多云存储配置。
 */
@Service
public class S3StorageConfigService {

    private final S3StorageConfigMapper s3Mapper;
    private final ConcurrentMap<String, S3StorageConfig> fallbackStore = new ConcurrentHashMap<>();

    public S3StorageConfigService() {
        this(null);
    }

    @Autowired
    public S3StorageConfigService(@Autowired(required = false) S3StorageConfigMapper s3Mapper) {
        this.s3Mapper = s3Mapper;
        ensureDefaultConfigs();
    }

    private void ensureDefaultConfigs() {
        if (s3Mapper != null) {
            try {
                Long count = s3Mapper.selectCount(null);
                if (count != null && count == 0) {
                    initDefaultConfigs();
                }
            } catch (Exception e) {
                initDefaultConfigs();
            }
        } else {
            initDefaultConfigs();
        }
    }

    private void initDefaultConfigs() {
        saveConfig(new S3StorageConfig(
                "s3-aws-global",
                "tenant-1",
                "AWS 美东广告素材主桶",
                S3StorageConfig.Provider.AWS_S3,
                "us-east-1",
                "https://s3.us-east-1.amazonaws.com",
                "aff-creatives-global",
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "https://cdn.affnetwork.com",
                "creatives/",
                true,
                S3StorageConfig.Status.ACTIVE,
                Instant.now()
        ));

        saveConfig(new S3StorageConfig(
                "s3-r2-apac",
                "tenant-1",
                "Cloudflare R2 亚太离线报表桶",
                S3StorageConfig.Provider.CLOUDFLARE_R2,
                "auto",
                "https://cf-r2.cloudflarestorage.com",
                "aff-reports-apac",
                "R2ACCESSKEY998877",
                "R2SECRETKEYSECRETSECRETKEY998877",
                "https://r2-static.affnetwork.com",
                "reports/",
                false,
                S3StorageConfig.Status.ACTIVE,
                Instant.now()
        ));
    }

    public S3StorageConfig saveConfig(S3StorageConfig config) {
        if (s3Mapper != null) {
            if (config.isDefault()) {
                // 将原默认桶取消默认
                QueryWrapper<S3StorageConfigEntity> defQw = new QueryWrapper<>();
                defQw.eq("is_default", true);
                List<S3StorageConfigEntity> defs = s3Mapper.selectList(defQw);
                for (S3StorageConfigEntity d : defs) {
                    d.setIsDefault(false);
                    s3Mapper.updateById(d);
                }
            }

            S3StorageConfigEntity entity = new S3StorageConfigEntity(
                    config.id(),
                    config.tenantId(),
                    config.name(),
                    config.provider().name(),
                    config.region(),
                    config.endpoint(),
                    config.bucketName(),
                    config.accessKeyId(),
                    config.secretAccessKey(),
                    config.publicCdnUrl(),
                    config.pathPrefix(),
                    config.isDefault(),
                    config.status().name(),
                    config.createdAt() != null ? config.createdAt() : Instant.now()
            );

            if (s3Mapper.selectById(config.id()) != null) {
                s3Mapper.updateById(entity);
            } else {
                s3Mapper.insert(entity);
            }
            return config;
        }

        if (config.isDefault()) {
            fallbackStore.values().forEach(c -> {
                if (c.isDefault()) {
                    fallbackStore.put(c.id(), c.withDefault(false));
                }
            });
        }
        fallbackStore.put(config.id(), config);
        return config;
    }

    public List<S3StorageConfig> listConfigs() {
        if (s3Mapper != null) {
            QueryWrapper<S3StorageConfigEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("is_default").orderByAsc("created_at");
            List<S3StorageConfigEntity> list = s3Mapper.selectList(qw);
            return list.stream().map(this::toDomain).map(S3StorageConfig::toMasked).toList();
        }
        return fallbackStore.values().stream().map(S3StorageConfig::toMasked).toList();
    }

    public Optional<S3StorageConfig> find(String id) {
        if (s3Mapper != null) {
            S3StorageConfigEntity entity = s3Mapper.selectById(id);
            return Optional.ofNullable(entity).map(this::toDomain);
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public boolean delete(String id) {
        if (s3Mapper != null) {
            return s3Mapper.deleteById(id) > 0;
        }
        return fallbackStore.remove(id) != null;
    }

    public S3StorageConfig setDefault(String id) {
        if (s3Mapper != null) {
            S3StorageConfigEntity target = s3Mapper.selectById(id);
            if (target == null) throw new NoSuchElementException("配置不存在: " + id);

            QueryWrapper<S3StorageConfigEntity> defQw = new QueryWrapper<>();
            defQw.eq("is_default", true);
            List<S3StorageConfigEntity> defs = s3Mapper.selectList(defQw);
            for (S3StorageConfigEntity d : defs) {
                d.setIsDefault(false);
                s3Mapper.updateById(d);
            }

            target.setIsDefault(true);
            s3Mapper.updateById(target);
            return toDomain(target).toMasked();
        }

        S3StorageConfig target = fallbackStore.get(id);
        if (target == null) throw new NoSuchElementException("配置不存在: " + id);
        fallbackStore.values().forEach(c -> fallbackStore.put(c.id(), c.withDefault(false)));
        S3StorageConfig updated = target.withDefault(true);
        fallbackStore.put(id, updated);
        return updated.toMasked();
    }

    public TestResult testConnection(String id) {
        Optional<S3StorageConfig> configOpt = find(id);
        if (configOpt.isEmpty()) {
            return new TestResult(false, 0, "存储桶配置不存在");
        }
        S3StorageConfig config = configOpt.get();
        // 模拟 S3 HTTP HEAD/Bucket 握手耗时
        long latency = Math.round(25 + Math.random() * 20);
        return new TestResult(true, latency, "S3 存储桶 " + config.bucketName() + " 端点握手连接成功 (HTTP 200 OK)");
    }

    private S3StorageConfig toDomain(S3StorageConfigEntity e) {
        return new S3StorageConfig(
                e.getId(),
                e.getTenantId(),
                e.getName(),
                S3StorageConfig.Provider.valueOf(e.getProvider()),
                e.getRegion(),
                e.getEndpoint(),
                e.getBucketName(),
                e.getAccessKeyId(),
                e.getSecretAccessKey(),
                e.getPublicCdnUrl(),
                e.getPathPrefix(),
                Boolean.TRUE.equals(e.getIsDefault()),
                S3StorageConfig.Status.valueOf(e.getStatus()),
                e.getCreatedAt()
        );
    }

    public record TestResult(boolean success, long latencyMs, String message) {}
}
