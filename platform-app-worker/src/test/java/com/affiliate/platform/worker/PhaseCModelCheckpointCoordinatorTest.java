package com.affiliate.platform.worker;

import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.dsp.Campaign;
import com.affiliate.platform.dsp.DeepCtrCvrInferenceEngine;
import com.affiliate.platform.dsp.DynamicBidShadingEngine;
import com.affiliate.platform.dsp.TrafficContext;
import com.affiliate.platform.ssp.DynamicYieldManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@DisplayName("Phase C 验证：FM / BidShading / DynamicYield 跨节点 Redis Checkpoint 持久化与热恢复测试")
class PhaseCModelCheckpointCoordinatorTest {

    @Test
    @DisplayName("验证 ModelCheckpointCoordinator 导出并热恢复三类在线模型与市场画像快照")
    @SuppressWarnings("unchecked")
    void testCheckpointAndRestoreAcrossColdStartEngines() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Map<String, String> redisStore = new ConcurrentHashMap<>();
        when(mockRedis.opsForValue()).thenReturn(mockOps);
        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockOps).set(anyString(), anyString(), any(Duration.class));
        when(mockOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));

        // 1. 节点 A 产生在线学习与市场反馈
        DeepCtrCvrInferenceEngine fmA = new DeepCtrCvrInferenceEngine();
        TrafficContext ctx = TrafficContext.of("tech.example.com", 1, LocalDate.now());
        Campaign camp = new Campaign("c_cp", "adv_cp", "Checkpoint Camp",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                BigDecimal.valueOf(1000), BigDecimal.valueOf(10),
                Set.of(), Set.of(), Campaign.Status.ACTIVE, Instant.now());
        for (int i = 0; i < 6; i++) {
            fmA.updateModel(ctx, camp, BigDecimal.valueOf(1.50), true, true);
        }
        DeepCtrCvrInferenceEngine.PredictionResult learnedPred = fmA.predict(ctx, camp, BigDecimal.valueOf(1.50));

        DynamicBidShadingEngine shadingA = new DynamicBidShadingEngine();
        for (int i = 0; i < 12; i++) {
            shadingA.recordWin("slot_1:US:mobile", 4.00, 3.10);
        }

        DynamicYieldManager yieldA = new DynamicYieldManager();
        for (int i = 0; i < 10; i++) {
            yieldA.recordAuctionOutcome("slot_1", true, 5.00, 4);
        }
        AdSlot slot = new AdSlot("slot_1", "Top", 300, 250, 2.0, true, true, Instant.now());
        DynamicYieldManager.FloorPriceResult expectedFloor = yieldA.resolveEwmaAdaptiveFloor(slot, "US", 1, 12);

        ModelCheckpointCoordinator writerCoordinator = new ModelCheckpointCoordinator(
                mockRedis, null, fmA, shadingA, yieldA
        );
        int saved = writerCoordinator.checkpointAll();
        assertEquals(3, saved);

        // 2. 节点 B 冷启动并从 Redis 恢复快照
        DeepCtrCvrInferenceEngine fmB = new DeepCtrCvrInferenceEngine();
        DynamicBidShadingEngine shadingB = new DynamicBidShadingEngine();
        DynamicYieldManager yieldB = new DynamicYieldManager();

        ModelCheckpointCoordinator readerCoordinator = new ModelCheckpointCoordinator(
                mockRedis, null, fmB, shadingB, yieldB
        );
        int restored = readerCoordinator.restoreAll();
        assertEquals(3, restored);

        DeepCtrCvrInferenceEngine.PredictionResult restoredPred = fmB.predict(ctx, camp, BigDecimal.valueOf(1.50));
        assertEquals(6L, fmB.getOnlineUpdateCount());
        assertEquals(learnedPred.pCtr(), restoredPred.pCtr(), 1e-12);
        assertEquals(learnedPred.pCvr(), restoredPred.pCvr(), 1e-12);

        assertEquals(expectedFloor.hardFloor(), yieldB.resolveEwmaAdaptiveFloor(slot, "US", 1, 12).hardFloor());
        assertEquals(12L, shadingB.exportSnapshot().profiles().get("slot_1:US:mobile").sampleCount());
    }
}
