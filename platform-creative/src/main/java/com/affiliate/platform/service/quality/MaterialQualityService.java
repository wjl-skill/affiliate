package com.affiliate.platform.service.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.quality.MaterialBenchmarkRepository;
import com.affiliate.platform.repository.quality.MaterialQualityHistoryRepository;
import com.affiliate.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 素材质量评估与大盘基准应用服务 (Material Quality Application Service)
 * <p>
 * 编排基准线读取、质量打标引擎、快照归档与跨系统领域事件发布。
 */
@Service
public class MaterialQualityService {

    private static final Logger log = LoggerFactory.getLogger(MaterialQualityService.class);

    private final MaterialBenchmarkRepository benchmarkRepo;
    private final MaterialQualityHistoryRepository historyRepo;
    private final MaterialQualityTagRecommendEngine recommendEngine;
    private final EventPublisher events;

    public MaterialQualityService(
            MaterialBenchmarkRepository benchmarkRepo,
            MaterialQualityHistoryRepository historyRepo,
            MaterialQualityTagRecommendEngine recommendEngine
    ) {
        this(benchmarkRepo, historyRepo, recommendEngine, e -> {});
    }

    @Autowired
    public MaterialQualityService(
            MaterialBenchmarkRepository benchmarkRepo,
            MaterialQualityHistoryRepository historyRepo,
            MaterialQualityTagRecommendEngine recommendEngine,
            EventPublisher events
    ) {
        this.benchmarkRepo = benchmarkRepo;
        this.historyRepo = historyRepo;
        this.recommendEngine = recommendEngine;
        this.events = events;
    }

    /**
     * 评估素材投放表现并持久化打标历史
     *
     * @param performance 素材 7 日投放事实
     * @return 评级结果快照
     */
    public MaterialQualityResult evaluateAndRecord(MaterialPerformance7D performance) {
        if (performance == null) {
            throw new IllegalArgumentException("performance cannot be null");
        }

        String tenantId = tenant();
        LocalDate date = performance.statDate() != null ? performance.statDate() : LocalDate.now();

        // 1. 获取最新一期大盘基准线；若无则自动生成并持久化默认基准
        MaterialBenchmark7D benchmark = benchmarkRepo.findLatest(tenantId)
                .orElseGet(() -> {
                    MaterialBenchmark7D defaultBm = MaterialBenchmark7D.createDefault(tenantId, date);
                    return benchmarkRepo.save(defaultBm);
                });

        // 2. 引擎执行严格 6 级短路评定
        MaterialQualityResult result = recommendEngine.evaluate(performance, benchmark);

        // 3. 落盘历史快照
        historyRepo.save(result);

        // 4. 发布素材打标完成领域事件 (通知 DSP 调整出价、TDS 调整权重)
        events.publish(DomainEvent.create(
                "creative.quality.tagged.v1",
                tenantId,
                result.creativeId(),
                result
        ));

        log.info("[MaterialQuality] creativeId={}, level={}, score={}, reasonTags={}",
                result.creativeId(), result.qualityLevel(), result.score(), result.reasonTags());

        return result;
    }

    /**
     * 保存或更新 7 日大盘质量基准线 (通常由数仓定时 ETL 任务调用)
     */
    public MaterialBenchmark7D saveBenchmark(MaterialBenchmark7D benchmark) {
        if (benchmark == null) {
            throw new IllegalArgumentException("benchmark cannot be null");
        }
        MaterialBenchmark7D saved = benchmarkRepo.save(benchmark);
        events.publish(DomainEvent.create(
                "creative.benchmark.updated.v1",
                tenant(),
                saved.id(),
                saved
        ));
        log.info("[MaterialQuality] benchmark updated: id={}, statDate={}, roasP75={}, costP50={}",
                saved.id(), saved.statDate(), saved.roasP75(), saved.costP50());
        return saved;
    }

    /**
     * 获取最新一期大盘质量基准线
     */
    public MaterialBenchmark7D getLatestBenchmark() {
        return benchmarkRepo.findLatest(tenant())
                .orElseGet(() -> MaterialBenchmark7D.createDefault(tenant(), LocalDate.now()));
    }

    /**
     * 获取指定日期的基准线
     */
    public Optional<MaterialBenchmark7D> getBenchmarkByDate(LocalDate date) {
        return benchmarkRepo.findByDate(tenant(), date);
    }

    /**
     * 获取指定素材的最新一期评级结果
     */
    public Optional<MaterialQualityResult> getLatestResult(String creativeId) {
        return historyRepo.findLatestByCreativeId(tenant(), creativeId);
    }

    /**
     * 查询指定素材的历史评级记录
     */
    public List<MaterialQualityResult> getHistory(String creativeId, int limit) {
        return historyRepo.findByCreativeId(tenant(), creativeId, limit);
    }

    /**
     * 按等级和日期筛选素材评级列表
     */
    public List<MaterialQualityResult> listByLevel(MaterialQualityLevel level, LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return historyRepo.findByDateAndLevel(tenant(), d, level);
    }

    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.get();
    }
}
