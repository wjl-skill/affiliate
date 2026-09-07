package com.affiliate.platform.tenant;

import com.affiliate.platform.tenant.system.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemConfigTest {

    private S3StorageConfigService s3Service;
    private TrackingDomainService domainService;
    private SystemConfigController controller;

    @BeforeEach
    void setUp() {
        s3Service = new S3StorageConfigService();
        domainService = new TrackingDomainService();
        controller = new SystemConfigController(s3Service, domainService);
    }

    @Test
    void s3StorageConfigCrudAndTest() {
        List<S3StorageConfig> list = controller.listS3();
        assertFalse(list.isEmpty());

        // 新建 S3 桶
        S3StorageConfig created = controller.createS3(Map.of(
                "name", "MinIO Local Dev Bucket",
                "provider", "MINIO",
                "endpoint", "http://minio.local:9000",
                "bucketName", "test-bucket",
                "accessKeyId", "minioadmin",
                "secretAccessKey", "miniopassword"
        ));
        assertNotNull(created);
        assertEquals("MINIO", created.provider().name());
        assertTrue(created.secretAccessKey().contains("****")); // 检验脱敏

        // 连通性测试
        S3StorageConfigService.TestResult result = controller.testS3(created.id());
        assertTrue(result.success());
        assertTrue(result.latencyMs() > 0);
    }

    @Test
    void trackingDomainLifecycleAndDnsVerify() {
        List<TrackingDomain> list = controller.listDomains();
        assertFalse(list.isEmpty());

        // 新增跟踪域名
        TrackingDomain created = controller.createDomain(Map.of(
                "domain", "trk.vip-affiliate.com",
                "domainType", "TRACKING",
                "cnameTarget", "lb.affnetwork.com",
                "assignedAffiliateId", "aff-vip-888"
        ));
        assertEquals(TrackingDomain.DnsStatus.PENDING_CNAME, created.dnsStatus());

        // 验证 DNS
        TrackingDomain verified = controller.verifyDns(created.id());
        assertEquals(TrackingDomain.DnsStatus.VERIFIED, verified.dnsStatus());
    }
}
