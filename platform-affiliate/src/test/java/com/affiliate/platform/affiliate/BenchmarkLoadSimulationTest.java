package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.service.AffiliateAntiFraudEngine;
import com.affiliate.platform.affiliate.service.ClickTrackerService;
import com.affiliate.platform.affiliate.service.OfferService;
import com.affiliate.platform.affiliate.service.PublisherPostbackDispatcher;
import com.affiliate.platform.affiliate.service.S2sPostbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("端到端万级并发性能基准压测套件 (Load Testing & P99 Benchmark)")
public class BenchmarkLoadSimulationTest {

    private ClickTrackerService clickTracker;
    private OfferService offerService;
    private AffiliateAntiFraudEngine antiFraudEngine;
    private PublisherPostbackDispatcher postbackDispatcher;
    private S2sPostbackService postbackService;

    @BeforeEach
    void setUp() {
        clickTracker = new ClickTrackerService();
        offerService = new OfferService();
        antiFraudEngine = new AffiliateAntiFraudEngine(null, null);
        postbackDispatcher = new PublisherPostbackDispatcher();

        postbackService = new S2sPostbackService(
                clickTracker,
                offerService,
                antiFraudEngine,
                postbackDispatcher
        );

        // 初始化基准 Offer
        offerService.save(new Offer(
                "off_bench_1", "public", "adv_bench_1", "E-Commerce Benchmark Sale",
                "https://advertiser.com/landing?sub1={sub1}",
                Offer.PayoutType.CPS, new BigDecimal("0.10"), new BigDecimal("0.15"),
                Offer.Status.ACTIVE, 1000000, new BigDecimal("1000000.00"), null,
                Set.of("US"), Set.of(1, 2), null, Instant.now()
        ));
    }

    @Test
    @DisplayName("压测 1: 点击流热路径 (Inbound Click) 5,000 次高并发吞吐与 P99 延时基准")
    void testClickStreamThroughputAndP99() throws InterruptedException {
        int totalRequests = 5000;
        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long[] durationsNano = new long[totalRequests];
        AtomicInteger index = new AtomicInteger(0);

        Offer benchOffer = offerService.find("off_bench_1").orElseThrow();
        long testStart = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    ClickTrackerService.ClickTrackingResult result = clickTracker.trackClick(
                            benchOffer,
                            "aff_" + (id % 100),
                            "sub_bench",
                            "kw_" + id,
                            null, null, null,
                            "192.168.1." + (id % 250),
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)",
                            "US",
                            1
                    );
                    assertNotNull(result);
                    assertNotNull(result.clickId());
                } finally {
                    long elapsed = System.nanoTime() - start;
                    int idx = index.getAndIncrement();
                    if (idx < totalRequests) {
                        durationsNano[idx] = elapsed;
                    }
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long totalTestTimeNano = System.nanoTime() - testStart;
        executor.shutdown();

        assertTrue(completed, "点击高并发压测应在 30 秒内全部完成");

        BenchmarkStats stats = calculateStats("点击热路径 (Click Tracking)", durationsNano, totalTestTimeNano);
        printBenchmarkReport(stats);

        // 验证 SLA: P99 < 20ms (内存及削峰模式通常远小于 1ms)
        assertTrue(stats.p99Ms < 20.0, "点击流热路径 P99 延时必须满足 SLA < 20ms 要求");
    }

    @Test
    @DisplayName("压测 2: S2S 服务端转化与防重排他锁 2,500 次高并发基准")
    void testS2sPostbackThroughputAndP99() throws InterruptedException {
        Offer benchOffer = offerService.find("off_bench_1").orElseThrow();
        // 先生成 500 个有效点击会话
        List<String> validClickIds = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ClickTrackerService.ClickTrackingResult res = clickTracker.trackClick(
                    benchOffer, "aff_postback_" + (i % 20), "sub1", null, null, null, null,
                    "203.0.113." + (i % 250), "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "US", 2
            );
            validClickIds.add(res.clickId());
        }

        // JIT 预热 100 次消除类加载与反射初始化冷启动抖动
        for (int w = 0; w < 100; w++) {
            String warmClickId = validClickIds.get(w % validClickIds.size());
            postbackService.processPostback(warmClickId, "warmup_tx_" + w, new BigDecimal("10.00"), Instant.now().plusSeconds(10));
        }

        int totalRequests = 2500;
        int concurrency = 40;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long[] durationsNano = new long[totalRequests];
        AtomicInteger index = new AtomicInteger(0);
        long testStart = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    String clickId = validClickIds.get(id % validClickIds.size());
                    // 制造部分冲突的 txId 验证并发排他防重
                    String txId = "bench_tx_" + (id % 1500);
                    Conversion conv = postbackService.processPostback(
                            clickId,
                            txId,
                            new BigDecimal("88.50"),
                            Instant.now().plusSeconds(10) // 10秒后转化 (避免 <3s 拦截)
                    );
                    assertNotNull(conv);
                } finally {
                    long elapsed = System.nanoTime() - start;
                    int idx = index.getAndIncrement();
                    if (idx < totalRequests) {
                        durationsNano[idx] = elapsed;
                    }
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long totalTestTimeNano = System.nanoTime() - testStart;
        executor.shutdown();

        assertTrue(completed, "S2S 转化高并发压测应在 30 秒内全部完成");

        BenchmarkStats stats = calculateStats("S2S 转化回传 (S2S Postback)", durationsNano, totalTestTimeNano);
        printBenchmarkReport(stats);

        assertTrue(stats.p99Ms < 25.0, "S2S 转化处理 P99 延时必须满足 SLA < 25ms 要求");
    }

    @Test
    @DisplayName("压测 3: 多维反欺诈与设备质检引擎 5,000 次高并发吞吐基准")
    void testAntiFraudEngineThroughputAndP99() throws InterruptedException {
        int totalRequests = 5000;
        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long[] durationsNano = new long[totalRequests];
        AtomicInteger index = new AtomicInteger(0);

        Instant clickTime = Instant.now().minusSeconds(120);
        ClickSession sampleSession = new ClickSession(
                "c_bench_af", "public", "off_bench_1", "aff_bench",
                "sub1", null, null, null, null,
                "198.51.100.1", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)",
                "US", 1, clickTime, clickTime.plusSeconds(3600)
        );

        // JIT 预热 100 次消除冷启动初始化抖动
        for (int w = 0; w < 100; w++) {
            antiFraudEngine.inspectConversion(
                    sampleSession, "warmup_af_" + w, Instant.now(),
                    "198.51.100.1", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)", "US", 1
            );
        }

        long testStart = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    AffiliateAntiFraudEngine.FraudInspectionResult res = antiFraudEngine.inspectConversion(
                            sampleSession,
                            "tx_af_" + id,
                            Instant.now(),
                            "198.51.100.1",
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)",
                            "US",
                            1
                    );
                    assertNotNull(res);
                } finally {
                    long elapsed = System.nanoTime() - start;
                    int idx = index.getAndIncrement();
                    if (idx < totalRequests) {
                        durationsNano[idx] = elapsed;
                    }
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long totalTestTimeNano = System.nanoTime() - testStart;
        executor.shutdown();

        assertTrue(completed, "反欺诈引擎高并发压测应在 30 秒内全部完成");

        BenchmarkStats stats = calculateStats("反欺诈风控质检 (Anti-Fraud Engine)", durationsNano, totalTestTimeNano);
        printBenchmarkReport(stats);

        assertTrue(stats.p99Ms < 20.0, "反欺诈内存质检 P99 延时必须满足 SLA < 20ms 要求");
    }

    // ========== 性能度量统计与报表生成 ==========

    private record BenchmarkStats(
            String scenarioName,
            int totalRequests,
            double totalSeconds,
            double qps,
            double minMs,
            double maxMs,
            double meanMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms
    ) {}

    private BenchmarkStats calculateStats(String scenarioName, long[] durationsNano, long totalTimeNano) {
        Arrays.sort(durationsNano);
        int n = durationsNano.length;

        double totalMs = 0;
        for (long d : durationsNano) {
            totalMs += (d / 1_000_000.0);
        }

        double meanMs = totalMs / n;
        double minMs = durationsNano[0] / 1_000_000.0;
        double maxMs = durationsNano[n - 1] / 1_000_000.0;
        double p50Ms = durationsNano[(int) (n * 0.50)] / 1_000_000.0;
        double p90Ms = durationsNano[(int) (n * 0.90)] / 1_000_000.0;
        double p95Ms = durationsNano[(int) (n * 0.95)] / 1_000_000.0;
        double p99Ms = durationsNano[(int) (n * 0.99)] / 1_000_000.0;

        double totalSeconds = totalTimeNano / 1_000_000_000.0;
        double qps = n / totalSeconds;

        return new BenchmarkStats(scenarioName, n, totalSeconds, qps, minMs, maxMs, meanMs, p50Ms, p90Ms, p95Ms, p99Ms);
    }

    private void printBenchmarkReport(BenchmarkStats s) {
        System.out.println("================================================================================");
        System.out.printf("📊 性能基准测试结果 [%s]%n", s.scenarioName());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  总请求量   : %,d reqs%n", s.totalRequests());
        System.out.printf("  执行总耗时 : %.3f 秒%n", s.totalSeconds());
        System.out.printf("  实测吞吐量 : %,.2f QPS%n", s.qps());
        System.out.printf("  平均延时   : %.3f ms%n", s.meanMs());
        System.out.printf("  Min 延时   : %.3f ms%n", s.minMs());
        System.out.printf("  P50 中位数 : %.3f ms%n", s.p50Ms());
        System.out.printf("  P90 分位   : %.3f ms%n", s.p90Ms());
        System.out.printf("  P95 分位   : %.3f ms%n", s.p95Ms());
        System.out.printf("  P99 分位   : %.3f ms (SLA < 20ms: %s)%n", s.p99Ms(), s.p99Ms() < 20.0 ? "PASSED" : "FAILED");
        System.out.println("================================================================================");
    }
}
