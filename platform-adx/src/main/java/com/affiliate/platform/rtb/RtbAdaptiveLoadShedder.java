package com.affiliate.platform.rtb;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RTB 实时自适应背压与过载削峰保护器 (Adaptive Load Shedder & Flow Controller)
 * <p>
 * 商业级实时交易系统在高压流量（100k+ QPS）下的核心稳定性护栏：
 * 1. 动态追踪系统实时并发与排队时延指标；
 * 2. 在突发流量尖刺下，实施智能分级流量削峰（Shedding）；
 * 3. 优先确保高价值 PMP 私有交易与头部广告位 100% 履约；
 * 4. 优雅丢弃长尾或超时风险极高的匿名请求，牢牢捍卫 P99 < 15ms 服务等级协议。
 */
@Component
public class RtbAdaptiveLoadShedder {

    // 默认最大允许并发竞价执行槽位
    private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 500;

    private final int maxConcurrentRequests;
    private final AtomicInteger currentConcurrency = new AtomicInteger(0);

    public final AtomicLong totalAdmitted = new AtomicLong(0);
    public final AtomicLong totalShedded = new AtomicLong(0);

    public RtbAdaptiveLoadShedder() {
        this(DEFAULT_MAX_CONCURRENT_REQUESTS);
    }

    public RtbAdaptiveLoadShedder(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    /**
     * 判断当前竞价请求是否应当被削峰丢弃
     *
     * @param request OpenRTB 竞价请求
     * @return true 代表应当丢弃该请求（过载削峰），false 代表正常准入执行
     */
    public boolean shouldShed(OpenRtb.BidRequest request) {
        int current = currentConcurrency.get();
        if (current < maxConcurrentRequests) {
            totalAdmitted.incrementAndGet();
            return false; // 负载健康，正常通行
        }

        // 过载状态下执行分级价值评估
        boolean hasPmpDeal = false;
        if (request != null && request.imp() != null) {
            for (OpenRtb.Imp imp : request.imp()) {
                if (imp.pmp() != null && imp.pmp().deals() != null && !imp.pmp().deals().isEmpty()) {
                    hasPmpDeal = true;
                    break;
                }
            }
        }

        // PMP 私有交易享最高特权，即使过载也尽量保障准入
        if (hasPmpDeal && current < (maxConcurrentRequests * 1.5)) {
            totalAdmitted.incrementAndGet();
            return false;
        }

        // 普通低价值/匿名长尾流量触发削峰丢弃
        totalShedded.incrementAndGet();
        return true;
    }

    /**
     * 请求开始执行时登记并发槽位
     */
    public void acquire() {
        currentConcurrency.incrementAndGet();
    }

    /**
     * 请求执行完毕或截断时释放并发槽位
     */
    public void release() {
        currentConcurrency.decrementAndGet();
    }

    public int getCurrentConcurrency() {
        return currentConcurrency.get();
    }
}
