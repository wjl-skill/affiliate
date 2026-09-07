package com.affiliate.platform.budget;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PacingAndAlertTest {

    @Test
    void uniformPacingControlsDelivery() {
        PacingController uniform = new PacingController(PacingController.PacingMode.UNIFORM);

        BigDecimal budget = new BigDecimal("240.00"); // 每小时期望 10 元

        // 凌晨 1 点 (hour 1)，累计消耗 5 元 -> 远低于期望累计 20 元 -> 必须放行
        assertTrue(uniform.shouldBid(1, new BigDecimal("5.00"), budget));

        // 当日消耗已达 240 元 -> 100% 阻断
        assertFalse(uniform.shouldBid(12, new BigDecimal("240.00"), budget));
        assertFalse(uniform.shouldBid(12, new BigDecimal("250.00"), budget));
    }

    @Test
    void asapPacingAllowsFastDelivery() {
        PacingController asap = new PacingController(PacingController.PacingMode.ASAP);
        BigDecimal budget = new BigDecimal("100.00");

        // 凌晨 0 点消耗 90 元，ASAP 模式依旧放行，直到完全耗尽
        assertTrue(asap.shouldBid(0, new BigDecimal("90.00"), budget));
        assertFalse(asap.shouldBid(0, new BigDecimal("100.00"), budget));
    }

    @Test
    void budgetAlertLadderLevels() {
        AtomicInteger eventCount = new AtomicInteger(0);
        BudgetAlertService alertService = new BudgetAlertService(event -> eventCount.incrementAndGet());

        BigDecimal budget = new BigDecimal("1000.00");

        // 1. 消耗 400 元 -> 未达 50% 水位 -> 无告警
        assertEquals(0, alertService.checkAndAlert("t1", "c1", new BigDecimal("400.00"), budget));
        assertEquals(0, eventCount.get());

        // 2. 消耗 550 元 -> 触发 50% 告警
        assertEquals(50, alertService.checkAndAlert("t1", "c1", new BigDecimal("550.00"), budget));
        assertEquals(1, eventCount.get());

        // 3. 再次消耗 600 元 -> 依旧处于 50% 阶梯，防重复告警 -> 返回 0
        assertEquals(0, alertService.checkAndAlert("t1", "c1", new BigDecimal("600.00"), budget));
        assertEquals(1, eventCount.get());

        // 4. 消耗 850 元 -> 跨越至 80% 告警
        assertEquals(80, alertService.checkAndAlert("t1", "c1", new BigDecimal("850.00"), budget));
        assertEquals(2, eventCount.get());

        // 5. 消耗 1000 元 -> 触发 100% 耗尽告警
        assertEquals(100, alertService.checkAndAlert("t1", "c1", new BigDecimal("1000.00"), budget));
        assertEquals(3, eventCount.get());

        // 6. 次日重置
        alertService.reset("t1", "c1");
        assertEquals(50, alertService.checkAndAlert("t1", "c1", new BigDecimal("500.00"), budget));
    }

    @Test
    void testAdaptivePidPacingController() {
        AdaptivePidPacingController pid = new AdaptivePidPacingController();
        BigDecimal budget = new BigDecimal("1440.00"); // 1440 分钟，每分钟期望消耗 1.00 美元

        // 场景 1: 早晨 06:00 (第 360 分钟)，期望消耗 360 元，实际仅消耗 100 元 (严重落后) -> 满速 100% 参竞加速
        double probFast = pid.calculateBidProbability("t1", "c_pid_1", budget, new BigDecimal("100.00"), java.time.LocalTime.of(6, 0));
        assertEquals(1.0, probFast);

        // 场景 2: 早晨 06:00 (第 360 分钟)，实际已消耗 800 元 (严重超速) -> 自动刹车抑制参竞率
        double probSlow = pid.calculateBidProbability("t1", "c_pid_2", budget, new BigDecimal("800.00"), java.time.LocalTime.of(6, 0));
        assertTrue(probSlow < 0.80, "Bid probability should be throttled, was: " + probSlow);

        // 场景 3: 预算耗尽 (1440 元) -> 参竞率归零 0.0
        double probZero = pid.calculateBidProbability("t1", "c_pid_3", budget, new BigDecimal("1440.00"), java.time.LocalTime.of(12, 0));
        assertEquals(0.0, probZero);
    }
}
