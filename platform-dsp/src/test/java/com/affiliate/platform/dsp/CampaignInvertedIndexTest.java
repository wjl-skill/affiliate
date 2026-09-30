package com.affiliate.platform.dsp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CampaignInvertedIndexTest {

    @Test
    @DisplayName("倒排索引多维定向快速求交测试（域名、设备、日期有效性）")
    void testMultiDimensionalInvertedMatching() {
        CampaignInvertedIndex index = new CampaignInvertedIndex();

        // c1: 限制域名 example.com，设备 Mobile(1)
        Campaign c1 = new Campaign(
                "c-1", "adv-1", "Mobile-Example",
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(2),
                new BigDecimal("500"), new BigDecimal("5.00"),
                Set.of("example.com"), Set.of(1),
                Campaign.Status.ACTIVE, null
        );

        // c2: 限制设备 Desktop(3)，不限域名
        Campaign c2 = new Campaign(
                "c-2", "adv-2", "Desktop-UniversalDomain",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5),
                new BigDecimal("300"), new BigDecimal("3.00"),
                Set.of(), Set.of(3),
                Campaign.Status.ACTIVE, null
        );

        // c3: 全网全设备通用活动
        Campaign c3 = new Campaign(
                "c-3", "adv-3", "All-Universal",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                new BigDecimal("1000"), new BigDecimal("1.50"),
                Set.of(), Set.of(),
                Campaign.Status.ACTIVE, null
        );

        // c4: 已过期活动
        Campaign c4 = new Campaign(
                "c-4", "adv-1", "Expired-Campaign",
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(2),
                new BigDecimal("100"), new BigDecimal("2.00"),
                Set.of("example.com"), Set.of(1),
                Campaign.Status.ACTIVE, null
        );

        // c5: PAUSED 活动
        Campaign c5 = new Campaign(
                "c-5", "adv-1", "Paused-Campaign",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                new BigDecimal("100"), new BigDecimal("2.00"),
                Set.of("example.com"), Set.of(1),
                Campaign.Status.PAUSED, null
        );

        index.rebuild(List.of(c1, c2, c3, c4, c5));

        // 验证索引中只有 4 个 ACTIVE 活动（c5 排除在外）
        assertEquals(4, index.activeCampaignCount());

        // 场景 1: example.com + 设备 1 (Mobile)
        // 预期召回: c1 (完全吻合), c3 (全网通用)。c4 因已过期过滤，c2 因设备不匹配过滤
        TrafficContext ctx1 = TrafficContext.of("example.com", 1, LocalDate.now());
        List<Campaign> matched1 = index.match(ctx1);
        assertEquals(2, matched1.size());
        assertTrue(matched1.stream().anyMatch(c -> c.id().equals("c-1")));
        assertTrue(matched1.stream().anyMatch(c -> c.id().equals("c-3")));

        // 场景 2: news.com + 设备 3 (Desktop)
        // 预期召回: c2 (Desktop 通用域名), c3 (全网通用)
        TrafficContext ctx2 = TrafficContext.of("news.com", 3, LocalDate.now());
        List<Campaign> matched2 = index.match(ctx2);
        assertEquals(2, matched2.size());
        assertTrue(matched2.stream().anyMatch(c -> c.id().equals("c-2")));
        assertTrue(matched2.stream().anyMatch(c -> c.id().equals("c-3")));

        // 场景 3: portal.com + 设备 4 (CTV)
        // 预期召回: 仅 c3 (全网通用)
        TrafficContext ctx3 = TrafficContext.of("portal.com", 4, LocalDate.now());
        List<Campaign> matched3 = index.match(ctx3);
        assertEquals(1, matched3.size());
        assertEquals("c-3", matched3.get(0).id());
    }

    @Test
    @DisplayName("增量 upsert 与 remove 无锁快照热替换测试")
    void testIncrementalUpsertAndRemove() {
        CampaignInvertedIndex index = new CampaignInvertedIndex();

        Campaign c1 = new Campaign(
                "c-1", "adv-1", "Initial",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                new BigDecimal("500"), new BigDecimal("5.00"),
                Set.of("a.com"), Set.of(1),
                Campaign.Status.ACTIVE, null
        );
        index.upsert(c1);
        assertEquals(1, index.activeCampaignCount());

        // 增加 c2
        Campaign c2 = new Campaign(
                "c-2", "adv-1", "Second",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                new BigDecimal("500"), new BigDecimal("5.00"),
                Set.of("b.com"), Set.of(2),
                Campaign.Status.ACTIVE, null
        );
        index.upsert(c2);
        assertEquals(2, index.activeCampaignCount());

        // 更新 c1 为 PAUSED -> 自动被移除
        index.upsert(c1.activate(false));
        assertEquals(1, index.activeCampaignCount());
        assertTrue(index.match(TrafficContext.of("a.com", 1, LocalDate.now())).isEmpty());

        // 物理 remove c2
        index.remove("c-2");
        assertEquals(0, index.activeCampaignCount());
    }

    @Test
    @DisplayName("高并发读写无锁安全性压测（100 并发虚拟线程并发检索与后台热更）")
    void testConcurrentReadWriteSafety() throws InterruptedException {
        CampaignInvertedIndex index = new CampaignInvertedIndex();

        // 初始填充 100 个活动
        for (int i = 0; i < 100; i++) {
            Campaign c = new Campaign(
                    "camp-" + i, "adv-" + (i % 5), "Camp-" + i,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(5),
                    new BigDecimal("100"), new BigDecimal("2.0"),
                    Set.of("site-" + (i % 10) + ".com"), Set.of(i % 3),
                    Campaign.Status.ACTIVE, null
            );
            index.upsert(c);
        }

        int threads = 50;
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger totalMatches = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        if (tid == 0 && i % 100 == 0) {
                            // 模拟后台动态热更活动
                            Campaign updated = new Campaign(
                                    "camp-dynamic", "adv-dyn", "Dyn-" + i,
                                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                                    new BigDecimal("50"), new BigDecimal("1.0"),
                                    Set.of("site-0.com"), Set.of(0),
                                    Campaign.Status.ACTIVE, null
                            );
                            index.upsert(updated);
                        }

                        TrafficContext ctx = TrafficContext.of("site-0.com", 0, LocalDate.now());
                        List<Campaign> res = index.match(ctx);
                        totalMatches.addAndGet(res.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(totalMatches.get() > 0, "并发检索应成功召回结果");
        assertTrue(index.activeCampaignCount() >= 100);
    }
}
