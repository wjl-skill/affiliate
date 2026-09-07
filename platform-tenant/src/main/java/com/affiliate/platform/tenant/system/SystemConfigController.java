package com.affiliate.platform.tenant.system;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 系统基础设施配置 REST 控制器 (S3 & Domain Admin Controller)
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemConfigController {

    private final S3StorageConfigService s3Service;
    private final TrackingDomainService domainService;

    public SystemConfigController(S3StorageConfigService s3Service, TrackingDomainService domainService) {
        this.s3Service = s3Service;
        this.domainService = domainService;
    }

    // ==========================================
    // 1. S3 存储配置 (S3 Storage)
    // ==========================================
    @GetMapping("/s3")
    public List<S3StorageConfig> listS3() {
        return s3Service.listConfigs();
    }

    @PostMapping("/s3")
    @ResponseStatus(HttpStatus.CREATED)
    public S3StorageConfig createS3(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", "s3-" + System.currentTimeMillis() % 10000);
        String name = (String) body.get("name");
        String providerStr = (String) body.getOrDefault("provider", "AWS_S3");
        String region = (String) body.getOrDefault("region", "us-east-1");
        String endpoint = (String) body.getOrDefault("endpoint", "https://s3.amazonaws.com");
        String bucketName = (String) body.get("bucketName");
        String accessKeyId = (String) body.get("accessKeyId");
        String secretAccessKey = (String) body.get("secretAccessKey");
        String publicCdnUrl = (String) body.getOrDefault("publicCdnUrl", "");
        String pathPrefix = (String) body.getOrDefault("pathPrefix", "creatives/");
        boolean isDefault = Boolean.TRUE.equals(body.get("isDefault"));

        S3StorageConfig config = new S3StorageConfig(
                id,
                "tenant-1",
                name,
                S3StorageConfig.Provider.valueOf(providerStr),
                region,
                endpoint,
                bucketName,
                accessKeyId,
                secretAccessKey,
                publicCdnUrl,
                pathPrefix,
                isDefault,
                S3StorageConfig.Status.ACTIVE,
                Instant.now()
        );
        return s3Service.saveConfig(config).toMasked();
    }

    @PostMapping("/s3/{id}/test")
    public S3StorageConfigService.TestResult testS3(@PathVariable String id) {
        return s3Service.testConnection(id);
    }

    @PostMapping("/s3/{id}/default")
    public S3StorageConfig setS3Default(@PathVariable String id) {
        return s3Service.setDefault(id);
    }

    @DeleteMapping("/s3/{id}")
    public ResponseEntity<Void> deleteS3(@PathVariable String id) {
        return s3Service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // 2. 跟踪域名池管理 (Domain Management)
    // ==========================================
    @GetMapping("/domains")
    public List<TrackingDomain> listDomains() {
        return domainService.listDomains();
    }

    @PostMapping("/domains")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackingDomain createDomain(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", "dom-" + System.currentTimeMillis() % 10000);
        String domain = (String) body.get("domain");
        String typeStr = (String) body.getOrDefault("domainType", "TRACKING");
        String cnameTarget = (String) body.getOrDefault("cnameTarget", "lb-global.affnetwork.com");
        String assignedAffId = (String) body.get("assignedAffiliateId");
        boolean isDefault = Boolean.TRUE.equals(body.get("isDefault"));

        TrackingDomain item = new TrackingDomain(
                id,
                "tenant-1",
                domain,
                TrackingDomain.DomainType.valueOf(typeStr),
                cnameTarget,
                TrackingDomain.DnsStatus.PENDING_CNAME,
                TrackingDomain.SslStatus.AUTO_SSL_ACTIVE,
                assignedAffId,
                isDefault,
                TrackingDomain.Status.ACTIVE,
                Instant.now()
        );
        return domainService.saveDomain(item);
    }

    @PostMapping("/domains/{id}/verify-dns")
    public TrackingDomain verifyDns(@PathVariable String id) {
        return domainService.verifyDns(id);
    }

    @PostMapping("/domains/{id}/default")
    public TrackingDomain setDomainDefault(@PathVariable String id) {
        return domainService.setDefault(id);
    }

    @DeleteMapping("/domains/{id}")
    public ResponseEntity<Void> deleteDomain(@PathVariable String id) {
        return domainService.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
