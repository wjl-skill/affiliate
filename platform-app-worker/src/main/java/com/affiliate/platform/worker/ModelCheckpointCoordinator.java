package com.affiliate.platform.worker;

import com.affiliate.platform.dsp.DeepCtrCvrInferenceEngine;
import com.affiliate.platform.dsp.DynamicBidShadingEngine;
import com.affiliate.platform.ssp.DynamicYieldManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 在线模型与市场收益画像跨节点 Checkpoint 持久化协调器 (Model & Market Profile Checkpoint Coordinator)
 * <p>
 * 周期性将 DSP FM 权重、DSP Bid Shading 市场水位与 SSP DynamicYield 收益画像快照序列化至 Redis，
 * 并在新节点启动时热恢复，消除 Pod 重启或扩缩容引起的冷启动偏差。
 */
@Component
public class ModelCheckpointCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ModelCheckpointCoordinator.class);
    static final String KEY_FM_SNAPSHOT = "checkpoint:dsp:fm:v1";
    static final String KEY_SHADING_SNAPSHOT = "checkpoint:dsp:shading:v1";
    static final String KEY_YIELD_SNAPSHOT = "checkpoint:ssp:yield:v1";
    private static final Duration SNAPSHOT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DeepCtrCvrInferenceEngine fmEngine;
    private final DynamicBidShadingEngine shadingEngine;
    private final DynamicYieldManager yieldManager;
    private volatile long lastCheckpointedFmUpdates = -1L;

    @Autowired
    public ModelCheckpointCoordinator(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) DeepCtrCvrInferenceEngine fmEngine,
            @Autowired(required = false) DynamicBidShadingEngine shadingEngine,
            @Autowired(required = false) DynamicYieldManager yieldManager
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.fmEngine = fmEngine;
        this.shadingEngine = shadingEngine;
        this.yieldManager = yieldManager;
    }

    @PostConstruct
    public void onStartupRestore() {
        restoreAll();
    }

    @Scheduled(fixedDelay = 60_000)
    public int checkpointAll() {
        if (redisTemplate == null) {
            return 0;
        }
        int saved = 0;
        if (fmEngine != null) {
            long currentUpdates = fmEngine.getOnlineUpdateCount();
            if (currentUpdates != lastCheckpointedFmUpdates) {
                if (writeSnapshot(KEY_FM_SNAPSHOT, fmEngine.exportSnapshot())) {
                    lastCheckpointedFmUpdates = currentUpdates;
                    saved++;
                }
            }
        }
        if (shadingEngine != null) {
            if (writeSnapshot(KEY_SHADING_SNAPSHOT, shadingEngine.exportSnapshot())) {
                saved++;
            }
        }
        if (yieldManager != null) {
            if (writeSnapshot(KEY_YIELD_SNAPSHOT, yieldManager.exportSnapshot())) {
                saved++;
            }
        }
        return saved;
    }

    public int restoreAll() {
        if (redisTemplate == null) {
            return 0;
        }
        int restored = 0;
        if (fmEngine != null) {
            DeepCtrCvrInferenceEngine.ModelSnapshot snap =
                    readSnapshot(KEY_FM_SNAPSHOT, DeepCtrCvrInferenceEngine.ModelSnapshot.class);
            if (snap != null) {
                fmEngine.importSnapshot(snap);
                restored++;
            }
        }
        if (shadingEngine != null) {
            DynamicBidShadingEngine.ShadingProfileSnapshot snap =
                    readSnapshot(KEY_SHADING_SNAPSHOT, DynamicBidShadingEngine.ShadingProfileSnapshot.class);
            if (snap != null) {
                shadingEngine.importSnapshot(snap);
                restored++;
            }
        }
        if (yieldManager != null) {
            DynamicYieldManager.YieldStatsSnapshot snap =
                    readSnapshot(KEY_YIELD_SNAPSHOT, DynamicYieldManager.YieldStatsSnapshot.class);
            if (snap != null) {
                yieldManager.importSnapshot(snap);
                restored++;
            }
        }
        return restored;
    }

    private boolean writeSnapshot(String key, Object snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, SNAPSHOT_TTL);
            return true;
        } catch (Exception ex) {
            log.debug("[ModelCheckpoint] Failed to write checkpoint {}: {}", key, ex.getMessage());
            return false;
        }
    }

    private <T> T readSnapshot(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.debug("[ModelCheckpoint] Failed to read checkpoint {}: {}", key, ex.getMessage());
            return null;
        }
    }
}
