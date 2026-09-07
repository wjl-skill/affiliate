package com.affiliate.platform.rtb;

import com.affiliate.platform.budget.InMemoryBudgetService;
import com.affiliate.platform.budget.InMemoryFrequencyCapService;
import com.affiliate.platform.budget.LocalBudgetSliceService;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.domain.Auction;
import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.repository.InMemoryRepository;
import com.affiliate.platform.service.AdSlotService;
import com.affiliate.platform.service.CreativeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RtbLatencyBenchmarkTest {

    private OpenRtbAuctionService auctionService;
    private CreativeInvertedIndex creativeIndex;

    @BeforeEach
    void setup() {
        InMemoryRepository<AdSlot> slotRepo = new InMemoryRepository<>(AdSlot::id);
        InMemoryRepository<Creative> creativeRepo = new InMemoryRepository<>(Creative::id);
        InMemoryRepository<Auction> auctionRepo = new InMemoryRepository<>(Auction::id);

        AdSlotService slotService = new AdSlotService(slotRepo);
        CreativeService creativeService = new CreativeService(creativeRepo);
        RuleBasedBidStrategy strategy = new RuleBasedBidStrategy();
        InMemoryBudgetService budgetService = new InMemoryBudgetService();
        LocalBudgetSliceService localBudgetService = new LocalBudgetSliceService(budgetService);
        InMemoryFrequencyCapService freqService = new InMemoryFrequencyCapService();
        HmacTokenService hmacService = new HmacTokenService("benchmark-secret-key-32b-length!");
        creativeIndex = new CreativeInvertedIndex();

        auctionService = new OpenRtbAuctionService(
                slotService,
                creativeService,
                strategy,
                auctionRepo,
                budgetService,
                freqService,
                hmacService,
                localBudgetService,
                creativeIndex
        );

        // Populate 10 ad slots
        int[][] dimensions = {{300, 250}, {728, 90}, {320, 50}, {160, 600}, {300, 600}};
        for (int i = 0; i < 10; i++) {
            int[] dim = dimensions[i % dimensions.length];
            slotRepo.save(new AdSlot("slot_" + i, "Slot " + i, dim[0], dim[1], 0.5 + (i * 0.1), true, true, Instant.now()));
        }

        // Populate 1,000 creatives into the inverted index
        for (int i = 0; i < 1000; i++) {
            int[] dim = dimensions[i % dimensions.length];
            Creative c = new Creative(
                    "cr_" + i,
                    "Creative " + i,
                    CreativeType.BANNER,
                    "https://cdn.example.com/asset_" + i + ".png",
                    "https://example.com/landing_" + i,
                    dim[0],
                    dim[1],
                    Set.of("tech"),
                    true,
                    Instant.now()
            );
            creativeIndex.index(c);
        }

        // Preload budget for "default-advertiser"
        budgetService.setBudget("public", "campaign_default-advertiser", new BigDecimal("10000000.00"));
        localBudgetService.preloadSlice("public", "campaign_default-advertiser", new BigDecimal("1000000.00"));
    }

    @Test
    void testSub20msLatencyBenchmark() {
        int warmupIterations = 1000;
        int benchmarkIterations = 2000;

        // Warm up JVM JIT compiler
        for (int i = 0; i < warmupIterations; i++) {
            OpenRtb.BidRequest req = new OpenRtb.BidRequest(
                    "bench_warmup_" + i,
                    List.of(new OpenRtb.Imp("slot_0", new OpenRtb.Banner(300, 250, List.of()), null, 0.5, "USD")),
                    new OpenRtb.Site("benchmark.com", "/test", null),
                    new OpenRtb.Device("Mozilla/5.0", "127.0.0.1", 1, "US"),
                    new OpenRtb.User("warmup_user_" + i, null),
                    20
            );
            auctionService.bid(req);
        }

        // Benchmark execution
        List<Double> latenciesMs = new ArrayList<>(benchmarkIterations);
        for (int i = 0; i < benchmarkIterations; i++) {
            OpenRtb.BidRequest req = new OpenRtb.BidRequest(
                    "bench_req_" + i,
                    List.of(new OpenRtb.Imp("slot_0", new OpenRtb.Banner(300, 250, List.of()), null, 0.5, "USD")),
                    new OpenRtb.Site("benchmark.com", "/test", null),
                    new OpenRtb.Device("Mozilla/5.0", "127.0.0.1", 1, "US"),
                    new OpenRtb.User("bench_user_" + i, null),
                    20 // 20ms tmax
            );
            long start = System.nanoTime();
            OpenRtb.BidResponse response = auctionService.bid(req);
            long elapsedNanos = System.nanoTime() - start;
            double elapsedMs = elapsedNanos / 1_000_000.0;
            latenciesMs.add(elapsedMs);

            assertFalse(response.seatbid().isEmpty(), "Bid response should contain winning bid");
        }

        // Calculate statistics
        Collections.sort(latenciesMs);
        double avg = latenciesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double p50 = latenciesMs.get((int) (benchmarkIterations * 0.50));
        double p95 = latenciesMs.get((int) (benchmarkIterations * 0.95));
        double p99 = latenciesMs.get((int) (benchmarkIterations * 0.99));
        double max = latenciesMs.get(benchmarkIterations - 1);

        System.out.println("=================================================");
        System.out.println(String.format("RTB Latency Benchmark Results (%d iterations):", benchmarkIterations));
        System.out.println(String.format("  Avg Latency: %.3f ms", avg));
        System.out.println(String.format("  P50 Latency: %.3f ms", p50));
        System.out.println(String.format("  P95 Latency: %.3f ms", p95));
        System.out.println(String.format("  P99 Latency: %.3f ms", p99));
        System.out.println(String.format("  Max Latency: %.3f ms", max));
        System.out.println("=================================================");

        // Strict assertions for sub-20ms SLA
        assertTrue(avg < 1.0, "Average latency should be strictly < 1.0 ms, was " + avg);
        assertTrue(p99 < 5.0, "P99 latency should be strictly < 5.0 ms, was " + p99);
        assertTrue(max < 50.0, "Max latency must not exceed tolerance, was " + max);
    }
}
