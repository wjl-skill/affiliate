package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.*;
import com.affiliate.platform.tenant.system.S3StorageConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offer 流量补量与存储桶 Parquet 用户画像解析单元测试（含边界与异常防护测试）
 */
class OfferSupplementServiceTest {

    private static final String SAMPLE_PARQUET_PATH =
            "c:\\Users\\Administrator\\Downloads\\part-00000-8eb3e574-f749-4e15-820d-d957e5360834.c000.snappy.parquet";

    private OfferService offerService;
    private ClickTrackerService clickTrackerService;
    private SubIdAnalyticsService analyticsService;
    private ParquetUserProfileReader parquetReader;
    private OfferSupplementService supplementService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        clickTrackerService = new ClickTrackerService();
        analyticsService = new SubIdAnalyticsService();
        S3StorageConfigService s3Service = new S3StorageConfigService();
        parquetReader = new ParquetUserProfileReader(s3Service);
        supplementService = new OfferSupplementService(
                offerService,
                clickTrackerService,
                analyticsService,
                parquetReader
        );
    }

    @Test
    @DisplayName("1. 验证从真实 .snappy.parquet 文件或存储桶种子读取并解析 10 个用户设备字段")
    void testReadRealSnappyParquetUserProfiles() {
        ParquetUserProfileReader.ParquetReadBatch batch = parquetReader.readProfiles(
                "s3-aws-global",
                SAMPLE_PARQUET_PATH,
                "ios",
                0,
                5
        );

        assertNotNull(batch);
        assertEquals(5, batch.profiles().size());
        assertTrue(batch.columns().containsAll(List.of(
                "id", "gadvid", "idfa", "ip", "ua", "os", "ds_publisher_id", "ds_adx", "s", "m"
        )));

        Path localFile = Paths.get(SAMPLE_PARQUET_PATH);
        if (Files.exists(localFile)) {
            assertEquals(42793, batch.totalRowsInFile());
        }

        SupplementUserProfile first = batch.profiles().get(0);
        assertEquals("9DB378C6-22B5-407B-AAC5-8D47F7898E49", first.idfa());
        assertEquals("27.52.129.115", first.ip());
        assertEquals("ios", first.os());
        assertEquals("56cff4d39deefe246000001d", first.dsPublisherId());
        assertEquals("mvungle", first.dsAdx());
        assertEquals("ym_tw_ios", first.s());
        assertEquals(6, first.m());
        assertEquals("TW", first.inferCountry("US"));
    }

    @Test
    @DisplayName("2. 验证 Offer 补量全链路：读取 Parquet -> 补充并替换 AF/Adjust 宏参数 -> HTTP 推送 -> 记录 ClickSession")
    void testExecuteSupplementBatchForAppsFlyerAndAdjust() {
        Offer afOffer = offerService.save(new Offer(
                "off_af_tw_01",
                "tenant-1",
                "adv_game_tw",
                "TW iOS RPG CPI Offer",
                "https://app.appsflyer.com/id16889900?pid={aff_id}&clickid={click_id}&idfa={idfa}&ip={ip}&ua={ua}",
                Offer.PayoutType.CPI,
                BigDecimal.valueOf(3.50),
                BigDecimal.valueOf(5.00),
                Offer.Status.ACTIVE,
                1000,
                BigDecimal.valueOf(5000),
                null,
                Set.of("TW", "US"),
                Set.of(1),
                null,
                Instant.now()
        ));

        OfferSupplementRule rule = supplementService.saveRule(new OfferSupplementRule(
                null,
                "tenant-1",
                afOffer.id(),
                "aff_pub_88",
                "APPSFLYER",
                afOffer.landingPageUrl(),
                "s3-aws-global",
                SAMPLE_PARQUET_PATH,
                "ios",
                3,
                100,
                true,
                30,
                0,
                0L,
                "ACTIVE",
                null,
                Instant.now(),
                Instant.now()
        ));

        OfferSupplementService.SupplementExecutionResult res = supplementService.executeSupplementBatch(
                new OfferSupplementService.SupplementTriggerRequest(
                        "tenant-1",
                        rule.id(),
                        afOffer.id(),
                        "aff_pub_88",
                        "APPSFLYER",
                        afOffer.landingPageUrl(),
                        "s3-aws-global",
                        SAMPLE_PARQUET_PATH,
                        "ios",
                        3,
                        100,
                        0
                )
        );

        assertEquals(3, res.dispatchedCount());
        assertEquals(3, res.successCount());
        assertEquals(0, res.failureCount());
        assertEquals(3, res.nextOffset());

        for (OfferSupplementLog logEntry : res.logs()) {
            assertNotNull(logEntry.clickId());
            assertTrue(logEntry.clickId().startsWith("c_"));

            ClickSession session = clickTrackerService.findSession(logEntry.clickId());
            assertNotNull(session, "每次补量必须记录到 ClickTrackerService 会话存储中");
            assertEquals(afOffer.id(), session.offerId());
            assertEquals("aff_pub_88", session.affiliateId());
            assertEquals(logEntry.ip(), session.ip());
            assertEquals(logEntry.userAgent(), session.userAgent());
            assertEquals(logEntry.dsPublisherId(), session.sub1());
            assertEquals(logEntry.dsAdx(), session.sub2());
            assertEquals(logEntry.sourceTag(), session.sub3());
            assertEquals(logEntry.idfa(), session.sub4());

            String targetUrl = logEntry.targetUrl();
            assertDoesNotThrow(() -> URI.create(targetUrl), "生成的归因目标 URL 必须是合法的 RFC-2396 URI");
            assertTrue(targetUrl.contains("clickid=" + logEntry.clickId()));
            assertTrue(targetUrl.contains("idfa=" + logEntry.idfa()));
            assertTrue(targetUrl.contains("ip=" + logEntry.ip()));
            assertTrue(targetUrl.contains("af_siteid=" + logEntry.dsPublisherId()));
            assertTrue(targetUrl.contains("af_sub_siteid=" + logEntry.dsAdx()));
            assertTrue(targetUrl.contains("af_sub1=" + logEntry.sourceTag()));
        }

        List<OfferSupplementRule> rules = supplementService.listRules("tenant-1", afOffer.id());
        assertEquals(1, rules.size());
        assertEquals(3, rules.get(0).lastCursorOffset());
        assertEquals(3L, rules.get(0).totalDispatchedClicks());

        SupplementUserProfile sampleProfile = new SupplementUserProfile(
                "dev_01", "gaid-9988-7766", "", "1.160.1.235",
                "Dalvik/2.1.0 (Linux; U; Android 14)", "android",
                "pub_adjust_01", "topon", "ym_tw_android", 5
        );
        String adjustUrl = supplementService.buildAndSupplementAttributionUrl(
                "https://app.adjust.com/abc123xyz",
                "ADJUST",
                afOffer,
                "aff_pub_88",
                "c_test_adjust_001",
                sampleProfile,
                "TW"
        );
        assertDoesNotThrow(() -> URI.create(adjustUrl));
        assertTrue(adjustUrl.contains("click_id=c_test_adjust_001"));
        assertTrue(adjustUrl.contains("gps_adid=gaid-9988-7766"));
        assertTrue(adjustUrl.contains("ip_address=1.160.1.235"));
        assertTrue(adjustUrl.contains("publisher_id=pub_adjust_01"));
        assertTrue(adjustUrl.contains("subpublisher_id=topon"));
        assertTrue(adjustUrl.contains("s2s=1"));
    }

    @Test
    @DisplayName("3. 边界检测：游标越界回绕、不匹配 OS 过滤、URL #锚点保留、未知宏清理、Header 注入防护与多租户隔离")
    void testBoundaryAndEdgeCases() {
        // 3.1 游标越界回绕与负数游标边界
        ParquetUserProfileReader.ParquetReadBatch wrapBatch = parquetReader.readProfiles(
                "s3-aws-global",
                SAMPLE_PARQUET_PATH,
                "ios",
                999_999, // 远超总行数 42,793
                2
        );
        assertEquals(2, wrapBatch.profiles().size());
        assertTrue(wrapBatch.startOffset() >= 0 && wrapBatch.startOffset() < wrapBatch.totalRowsInFile());

        // 3.2 不存在的 OS 过滤条件边界（全表扫描一圈后安全返回空列表，不死循环）
        ParquetUserProfileReader.ParquetReadBatch emptyOsBatch = parquetReader.readProfiles(
                "s3-aws-global",
                SAMPLE_PARQUET_PATH,
                "harmonyos_non_existent",
                0,
                5
        );
        assertTrue(emptyOsBatch.profiles().isEmpty());

        // 3.3 非法 Parquet 字节数组边界防护
        assertThrows(IllegalArgumentException.class, () ->
                parquetReader.parseParquetBytes("bad.parquet", new byte[]{1, 2, 3, 4}, "ALL", 0, 10)
        );

        // 3.4 URL 包含 #fragment 锚点、未知 {custom_macro}、以及含空格/特殊字符的画像字段
        Offer edgeOffer = offerService.save(new Offer(
                "off_edge_01",
                "tenant-a",
                "adv_edge",
                "Edge Case Offer",
                "https://app.appsflyer.com/id999?pid={aff_id}&custom={unknown_macro_123}#deep_link_section",
                Offer.PayoutType.CPI,
                BigDecimal.ONE,
                BigDecimal.TEN,
                Offer.Status.ACTIVE,
                100,
                BigDecimal.valueOf(1000),
                null,
                Set.of(), // 空国家集合边界
                Set.of(1),
                null,
                Instant.now()
        ));

        SupplementUserProfile dirtyProfile = new SupplementUserProfile(
                "id with space",
                "",
                "IDFA-001",
                "27.52.129.115\r\nX-Injected:1",
                "Mozilla/5.0 (iPhone)\r\nBadHeader: true 中文UA",
                "ios",
                "pub id & special=1",
                "adx+name",
                "ym_jp_ios",
                6
        );

        String builtUrl = supplementService.buildAndSupplementAttributionUrl(
                edgeOffer.landingPageUrl(),
                "APPSFLYER",
                edgeOffer,
                "aff 01",
                "c_edge_01",
                dirtyProfile,
                null
        );

        // 验证未知宏已被清理、#deep_link_section 锚点位于 URL 最末尾、且 URI.create 100% 合法
        assertFalse(builtUrl.contains("{unknown_macro_123}"));
        assertTrue(builtUrl.endsWith("#deep_link_section"));
        assertDoesNotThrow(() -> URI.create(builtUrl));

        // 3.5 验证 HTTP Header 注入与非 ASCII 字符清洗
        String sanitizedUa = OfferSupplementService.sanitizeHttpHeaderValue(dirtyProfile.ua());
        assertFalse(sanitizedUa.contains("\r"));
        assertFalse(sanitizedUa.contains("\n"));
        assertFalse(sanitizedUa.contains("中文"));

        // 3.6 验证多租户隔离边界 (tenant-a 与 tenant-b 的规则互不可见)
        supplementService.saveRule(new OfferSupplementRule(
                "rule_tenant_a", "tenant-a", edgeOffer.id(), "aff_1", "APPSFLYER",
                edgeOffer.landingPageUrl(), "s3-aws-global", SAMPLE_PARQUET_PATH,
                "ios", 5, 20, false, 30, 0, 0L, "ACTIVE", null, Instant.now(), Instant.now()
        ));
        supplementService.saveRule(new OfferSupplementRule(
                "rule_tenant_b", "tenant-b", edgeOffer.id(), "aff_2", "ADJUST",
                edgeOffer.landingPageUrl(), "s3-aws-global", SAMPLE_PARQUET_PATH,
                "ios", 5, 20, false, 30, 0, 0L, "ACTIVE", null, Instant.now(), Instant.now()
        ));

        assertEquals(1, supplementService.listRules("tenant-a", edgeOffer.id()).size());
        assertEquals("rule_tenant_a", supplementService.listRules("tenant-a", edgeOffer.id()).get(0).id());
        assertEquals(1, supplementService.listRules("tenant-b", edgeOffer.id()).size());
        assertEquals("rule_tenant_b", supplementService.listRules("tenant-b", edgeOffer.id()).get(0).id());
    }

    @Test
    @DisplayName("4. 验证后台定时自动补量调度器结合分布式锁安全执行与自动互斥")
    void testScheduledAutoSupplementWithDistributedLock() {
        com.affiliate.platform.lock.RedisDistributedLock lockService = new com.affiliate.platform.lock.RedisDistributedLock(null);
        OfferSupplementService serviceWithLock = new OfferSupplementService(
                offerService,
                clickTrackerService,
                analyticsService,
                parquetReader,
                null,
                null,
                false,
                lockService
        );

        Offer offer = offerService.save(new Offer(
                "off_auto_01", "tenant-sched", "adv_sched", "Scheduled Offer",
                "https://app.appsflyer.com/id888?pid={aff_id}&clickid={click_id}",
                Offer.PayoutType.CPI, BigDecimal.ONE, BigDecimal.TEN,
                Offer.Status.ACTIVE, 1000, BigDecimal.valueOf(5000),
                null, Set.of("TW"), Set.of(1), null, Instant.now()
        ));

        // 保存一条 active & autoEnabled = true 的规则 (最后执行时间为 2 小时前，满足 cronIntervalMinutes = 30)
        OfferSupplementRule rule = serviceWithLock.saveRule(new OfferSupplementRule(
                "rule_auto_sched_01", "tenant-sched", offer.id(), "aff_auto", "APPSFLYER",
                offer.landingPageUrl(), "s3-aws-global", SAMPLE_PARQUET_PATH, "ios",
                3, 50, true, 30, 0, 0L, "ACTIVE",
                Instant.now().minusSeconds(7200), Instant.now(), Instant.now()
        ));

        // 执行调度巡检
        assertDoesNotThrow(serviceWithLock::runScheduledAutoSupplement);

        // 验证规则已被执行，游标推进至 3，总分发点击为 3
        List<OfferSupplementRule> rules = serviceWithLock.listRules("tenant-sched", offer.id());
        assertEquals(1, rules.size());
        assertEquals(3, rules.get(0).lastCursorOffset());
        assertEquals(3L, rules.get(0).totalDispatchedClicks());
        assertNotNull(rules.get(0).lastExecutedAt());
    }
}
