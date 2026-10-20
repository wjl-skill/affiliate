package com.affiliate.platform.ai.service;

import com.affiliate.platform.ai.model.AiUsage;
import com.affiliate.platform.entity.AiUsageLogEntity;
import com.affiliate.platform.mapper.AiUsageLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 租户级 AI Token 配额消耗与调用审计服务 (AI Usage & Metering Service)
 */
@Service
public class AiUsageMeteringService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageMeteringService.class);

    private final AiUsageLogMapper usageLogMapper;

    @Autowired
    public AiUsageMeteringService(@Autowired(required = false) AiUsageLogMapper usageLogMapper) {
        this.usageLogMapper = usageLogMapper;
    }

    public AiUsageMeteringService() {
        this(null);
    }

    public record TenantAiStats(
            String tenantId,
            long totalRequests,
            long totalTokens,
            long promptTokens,
            long completionTokens,
            double avgLatencyMs,
            Instant lastRequestTime
    ) {}

    public record AiLogEntry(
            String id,
            String tenantId,
            String callerModule,
            String modelName,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            String status,
            Instant timestamp
    ) {}

    private final Map<String, AtomicLong> tenantRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tenantTotalTokens = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tenantPromptTokens = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tenantCompletionTokens = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tenantTotalLatency = new ConcurrentHashMap<>();
    private final List<AiLogEntry> recentLogs = Collections.synchronizedList(new LinkedList<>());

    public void recordUsage(
            String tenantId,
            String callerModule,
            String modelName,
            AiUsage usage,
            long latencyMs,
            boolean fallbackUsed,
            String errorMsg
    ) {
        String tid = tenantId != null ? tenantId : "default";
        tenantRequests.computeIfAbsent(tid, k -> new AtomicLong()).incrementAndGet();
        tenantTotalLatency.computeIfAbsent(tid, k -> new AtomicLong()).addAndGet(latencyMs);

        if (usage != null) {
            tenantTotalTokens.computeIfAbsent(tid, k -> new AtomicLong()).addAndGet(usage.totalTokens());
            tenantPromptTokens.computeIfAbsent(tid, k -> new AtomicLong()).addAndGet(usage.promptTokens());
            tenantCompletionTokens.computeIfAbsent(tid, k -> new AtomicLong()).addAndGet(usage.completionTokens());
        }

        String status = errorMsg != null ? "FAILED" : (fallbackUsed ? "FALLBACK" : "SUCCESS");
        AiLogEntry entry = new AiLogEntry(
                "ailog_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tid,
                callerModule != null ? callerModule : "GENERAL",
                modelName != null ? modelName : "unknown",
                usage != null ? usage.promptTokens() : 0,
                usage != null ? usage.completionTokens() : 0,
                usage != null ? usage.totalTokens() : 0,
                latencyMs,
                status,
                Instant.now()
        );

        recentLogs.add(0, entry);
        if (recentLogs.size() > 500) {
            recentLogs.remove(recentLogs.size() - 1);
        }

        if (usageLogMapper != null) {
            try {
                usageLogMapper.insert(new AiUsageLogEntity(
                        entry.id(), entry.tenantId(), entry.callerModule(), entry.modelName(),
                        entry.promptTokens(), entry.completionTokens(), entry.totalTokens(),
                        entry.latencyMs(), entry.status(), errorMsg, entry.timestamp()
                ));
            } catch (Exception e) {
                log.debug("[AiUsageMeteringService] Database audit log write skipped: {}", e.getMessage());
            }
        }

        log.debug("[AiUsageMeteringService] Recorded AI usage: tenant={}, module={}, tokens={}, latency={}ms, status={}",
                tid, callerModule, usage != null ? usage.totalTokens() : 0, latencyMs, status);
    }

    public TenantAiStats getTenantStats(String tenantId) {
        String tid = tenantId != null ? tenantId : "default";
        long reqs = tenantRequests.getOrDefault(tid, new AtomicLong(0)).get();
        long totalTok = tenantTotalTokens.getOrDefault(tid, new AtomicLong(0)).get();
        long pTok = tenantPromptTokens.getOrDefault(tid, new AtomicLong(0)).get();
        long cTok = tenantCompletionTokens.getOrDefault(tid, new AtomicLong(0)).get();
        long totLatency = tenantTotalLatency.getOrDefault(tid, new AtomicLong(0)).get();
        double avgLat = reqs > 0 ? (double) totLatency / reqs : 0.0;

        return new TenantAiStats(tid, reqs, totalTok, pTok, cTok, avgLat, Instant.now());
    }

    public List<AiLogEntry> getRecentLogs(String tenantId, int limit) {
        String tid = tenantId != null ? tenantId : "default";
        return recentLogs.stream()
                .filter(l -> tid.equals(l.tenantId()) || "default".equals(tid))
                .limit(limit > 0 ? limit : 50)
                .toList();
    }
}
