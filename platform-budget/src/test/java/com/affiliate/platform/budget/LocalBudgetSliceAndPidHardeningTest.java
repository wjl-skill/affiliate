package com.affiliate.platform.budget;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class LocalBudgetSliceAndPidHardeningTest {

    @Test
    void testLocalBudgetSliceDrainAndCampaignPause() {
        InMemoryBudgetService mainBudget = new InMemoryBudgetService();
        mainBudget.setBudget("t1", "camp1", new BigDecimal("100.00"));

        LocalBudgetSliceService sliceService = new LocalBudgetSliceService(mainBudget);

        // 1. 快速预占 2.00 美元 -> 从主池拉取切片 (10.00) 并扣减 2.00 -> 本地剩余 8.00
        BudgetService.Reservation res1 = sliceService.reserveFast("t1", "camp1", "user1", new BigDecimal("2.00"));
        assertNotNull(res1);
        // 预取 Math.max(10 USD, 2 * 10 = 20 USD) = 20 USD，扣减 2 USD，本地切片结余 18 USD (18,000,000 micros)
        assertEquals(18_000_000L, sliceService.getLocalSliceMicros("t1", "camp1"));

        // 2. 胜出确认
        sliceService.confirm(res1);
        assertEquals(0, sliceService.getActiveReservationCount());

        // 3. 运营暂停活动：应立即触发本地切片排空归还，并将活动置为暂停状态
        sliceService.pauseCampaign("t1", "camp1");
        assertTrue(sliceService.isCampaignPaused("t1", "camp1"));
        assertEquals(0L, sliceService.getLocalSliceMicros("t1", "camp1"));

        // 4. 活动暂停后再次尝试竞价发标，必须直接阻断抛出异常，防止超支
        assertThrows(IllegalStateException.class, () ->
                sliceService.reserveFast("t1", "camp1", "user2", new BigDecimal("1.00"))
        );

        // 5. 恢复活动后可再次发标
        sliceService.resumeCampaign("t1", "camp1");
        assertFalse(sliceService.isCampaignPaused("t1", "camp1"));
    }

    @Test
    void testLeakedReservationSweeperPreventsMemoryLeak() throws InterruptedException {
        InMemoryBudgetService mainBudget = new InMemoryBudgetService();
        mainBudget.setBudget("t1", "camp_sweep", new BigDecimal("50.00"));

        LocalBudgetSliceService sliceService = new LocalBudgetSliceService(mainBudget);

        // 快速预占 3.00 美元，但模拟下游超时未调用 confirm 或 release
        BudgetService.Reservation leakedRes = sliceService.reserveFast("t1", "camp_sweep", "user_leaked", new BigDecimal("3.00"));
        assertNotNull(leakedRes);
        assertEquals(1, sliceService.getActiveReservationCount());

        // 触发超时扫描（超时时间设为 0ms 模拟强制回收）
        int recovered = sliceService.sweepExpiredReservations(0L);
        assertEquals(1, recovered);
        assertEquals(0, sliceService.getActiveReservationCount());

        // 预取 Math.max(10 USD, 3 * 10 = 30 USD) = 30 USD，扣减 3 USD 后超时回收补回 3 USD，切片总额 30 USD (30,000,000 micros)
        assertEquals(30_000_000L, sliceService.getLocalSliceMicros("t1", "camp_sweep"));

        // 优雅停机排空测试
        sliceService.drainAndReturnAllSlices();
        assertEquals(0L, sliceService.getLocalSliceMicros("t1", "camp_sweep"));
    }

    @Test
    void testAdaptivePidDiurnalCurveAndDayBoundaryReset() {
        AdaptivePidPacingController pid = new AdaptivePidPacingController();
        BigDecimal dailyBudget = new BigDecimal("1000.00");

        // 1. 验证 24 小时真实流量 CDF 单调递增且全天覆盖
        double cdfNight = pid.calculateDiurnalCdf(LocalTime.of(3, 0));
        double cdfNoon = pid.calculateDiurnalCdf(LocalTime.of(12, 0));
        double cdfPeak = pid.calculateDiurnalCdf(LocalTime.of(20, 0));

        assertTrue(cdfNight < cdfNoon, "Night CDF must be less than Noon CDF");
        assertTrue(cdfNoon < cdfPeak, "Noon CDF must be less than Peak CDF");
        assertTrue(cdfPeak <= 1.0, "Peak CDF must not exceed 1.0");

        // 2. 验证清晨 07:00 消耗落后时平滑参竞
        double probMorning = pid.calculateBidProbability(
                "t1", "camp_pid", dailyBudget, new BigDecimal("20.00"),
                LocalTime.of(7, 0), LocalDate.of(2026, 10, 8)
        );
        assertTrue(probMorning >= 0.90 && probMorning <= 1.00);

        // 3. 验证跨天午夜平滑重置，不发生导数突变失控
        double probNextDay = pid.calculateBidProbability(
                "t1", "camp_pid", dailyBudget, new BigDecimal("0.00"),
                LocalTime.of(0, 1), LocalDate.of(2026, 10, 9)
        );
        assertTrue(probNextDay >= 0.05 && probNextDay <= 1.00);
    }

    @Test
    void testSliceKeyWithColonsAndBroadcastEvent() {
        InMemoryBudgetService mainBudget = new InMemoryBudgetService();
        // 模拟带有多重冒号的复杂租户与活动 ID：tenant:sub:001, camp:us:retail:999
        mainBudget.setBudget("tenant:sub:001", "camp:us:retail:999", new BigDecimal("100.00"));

        LocalBudgetSliceService sliceService = new LocalBudgetSliceService(mainBudget);

        // 1. 预占 2 USD
        BudgetService.Reservation res = sliceService.reserveFast("tenant:sub:001", "camp:us:retail:999", "u1", new BigDecimal("2.00"));
        assertNotNull(res);
        assertEquals(18_000_000L, sliceService.getLocalSliceMicros("tenant:sub:001", "camp:us:retail:999"));

        // 2. 模拟跨节点广播活动暂停事件
        sliceService.onCampaignStatusChanged(new LocalBudgetSliceService.CampaignPauseEvent(
                "tenant:sub:001", "camp:us:retail:999", "Fraud risk detected", System.currentTimeMillis()
        ));

        // 验证已暂停且切片被排空
        assertTrue(sliceService.isCampaignPaused("tenant:sub:001", "camp:us:retail:999"));
        assertEquals(0L, sliceService.getLocalSliceMicros("tenant:sub:001", "camp:us:retail:999"));

        // 3. 停机排空不发生冒号截断异常
        assertDoesNotThrow(sliceService::drainAndReturnAllSlices);
    }
}
