package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.entity.ClickSessionEntity;
import com.affiliate.platform.mapper.ClickSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 渠道推广链接点击追踪服务 (Affiliate Click Tracker Service - MyBatis-Plus)
 * <p>
 * 响应前台 `/click` 请求：
 * 1. 生成加密密码级全局唯一 `click_id`；
 * 2. 捕获渠道传参 `sub1`~`sub5` 与环境特征，并持久化沉淀至 PostgreSQL `affiliate_click_session`；
 * 3. 对广告主目标落地页链接执行 `{click_id}` 与 `{sub1}` 等宏变量动态替换；
 * 4. 返回用于 302 重定向的目标落地页 URL。
 */
@Service
public class ClickTrackerService {

    private final StringRedisTemplate redisTemplate;
    private final ClickSessionMapper clickSessionMapper;
    private final ProbabilisticAttributionEngine probabilisticEngine;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClickTrackerService.class);
    // 工业级有界异步落盘线程池 (带 10000 容量有界队列，彻底消除高并发下的 OOM 内存溢出隐患)
    private final java.util.concurrent.ThreadPoolExecutor asyncDbWriter = new java.util.concurrent.ThreadPoolExecutor(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 16),
            Math.min(Runtime.getRuntime().availableProcessors() * 4, 32),
            60L,
            java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(10000),
            Thread.ofVirtual().name("click-db-writer-", 0).factory(),
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        asyncDbWriter.shutdown();
        try {
            if (!asyncDbWriter.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                asyncDbWriter.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncDbWriter.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // 内存降级存储容器
    private final ConcurrentMap<String, ClickSession> sessionStore = new ConcurrentHashMap<>();

    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    private final com.affiliate.platform.affiliate.metrics.AffiliateMetrics metrics;

    public ClickTrackerService() {
        this(null, null, null, null, null);
    }

    public ClickTrackerService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, null, null, null, null);
    }

    public ClickTrackerService(
            StringRedisTemplate redisTemplate,
            ClickSessionMapper clickSessionMapper,
            ProbabilisticAttributionEngine probabilisticEngine
    ) {
        this(redisTemplate, clickSessionMapper, probabilisticEngine, null, null);
    }

    public ClickTrackerService(
            StringRedisTemplate redisTemplate,
            ClickSessionMapper clickSessionMapper,
            ProbabilisticAttributionEngine probabilisticEngine,
            org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this(redisTemplate, clickSessionMapper, probabilisticEngine, kafkaTemplate, null);
    }

    @Autowired
    public ClickTrackerService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ClickSessionMapper clickSessionMapper,
            @Autowired(required = false) ProbabilisticAttributionEngine probabilisticEngine,
            @Autowired(required = false) org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate,
            @Autowired(required = false) com.affiliate.platform.affiliate.metrics.AffiliateMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.clickSessionMapper = clickSessionMapper;
        this.probabilisticEngine = probabilisticEngine;
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics != null ? metrics : new com.affiliate.platform.affiliate.metrics.AffiliateMetrics();
    }

    /**
     * 追踪记录点击并构建目标落地页重定向 URL
     *
     * @param offer       目标推广计划
     * @param affiliateId 渠道客标识
     * @param sub1        子渠道维度1
     * @param sub2        子渠道维度2
     * @param sub3        子渠道维度3
     * @param sub4        子渠道维度4
     * @param sub5        子渠道维度5
     * @param ip          客户端 IP
     * @param userAgent   客户端 User-Agent
     * @param country     国家代码
     * @param deviceType  设备形态
     * @return 注入宏参数后的目标落地页跳转 URL
     */
    public ClickTrackingResult trackClick(
            Offer offer,
            String affiliateId,
            String sub1, String sub2, String sub3, String sub4, String sub5,
            String ip, String userAgent, String country, int deviceType
    ) {
        if (offer == null) {
            throw new IllegalArgumentException("offer must not be null");
        }

        long startNanos = System.nanoTime();

        // 1. 生成全局唯一 click_id
        String clickId = "c_" + UUID.randomUUID().toString().replace("-", "");

        // 2. 构造点击会话存根 (默认 30 天归因窗口)
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(30));

        ClickSession session = new ClickSession(
                clickId,
                offer.tenantId(),
                offer.id(),
                affiliateId,
                sub1, sub2, sub3, sub4, sub5,
                ip, userAgent, country, deviceType,
                now, expiresAt
        );

        // 3. 沉淀会话存根至本地高可用内存容器与指纹池
        sessionStore.put(clickId, session);
        if (probabilisticEngine != null) {
            try {
                probabilisticEngine.registerClickFingerprint(session);
            } catch (Exception ignored) {
            }
        }

        // 4. Redis 分布式会话同步 (支持多节点微秒级 Postback 定位)
        if (redisTemplate != null) {
            try {
                String sessionData = String.join("||",
                        clickId,
                        offer.tenantId() != null ? offer.tenantId() : "public",
                        offer.id(),
                        affiliateId != null ? affiliateId : "",
                        sub1 != null ? sub1 : "",
                        sub2 != null ? sub2 : "",
                        sub3 != null ? sub3 : "",
                        sub4 != null ? sub4 : "",
                        sub5 != null ? sub5 : "",
                        ip != null ? ip : "",
                        userAgent != null ? userAgent : "",
                        country != null ? country : "",
                        String.valueOf(deviceType),
                        String.valueOf(now.toEpochMilli()),
                        String.valueOf(expiresAt.toEpochMilli())
                );
                redisTemplate.opsForValue().set("aff:click:sess:" + clickId, sessionData, Duration.ofDays(30));
                redisTemplate.opsForValue().set("aff:click:" + clickId, offer.id() + ":" + affiliateId, Duration.ofDays(30));
            } catch (Exception ex) {
                log.warn("Redis write failed for click session {}: {}", clickId, ex.getMessage());
            }
        }

        // 5. 极速数据面模式：优先异步直投 Kafka 削峰，解耦关系数据库写锁
        ClickSessionEntity entity = new ClickSessionEntity(
                clickId,
                offer.tenantId(),
                offer.id(),
                affiliateId,
                sub1, sub2, sub3, sub4, sub5,
                ip, userAgent, country, deviceType,
                now, expiresAt
        );

        if (kafkaTemplate != null) {
            try {
                java.util.concurrent.CompletableFuture<?> future =
                        kafkaTemplate.send("affiliate.events.click", clickId, entity);
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Kafka click dispatch async failed for {}, fallback to async db: {}", clickId, ex.getMessage());
                        if (clickSessionMapper != null) {
                            asyncDbWriter.submit(() -> {
                                try {
                                    clickSessionMapper.insert(entity);
                                } catch (Exception dbEx) {
                                    log.error("Async DB fallback insertion also failed for {}: {}", clickId, dbEx.getMessage());
                                }
                            });
                        }
                    }
                });
            } catch (Exception ex) {
                log.warn("Kafka click dispatch synchronous failed for {}, fallback to async db: {}", clickId, ex.getMessage());
                if (clickSessionMapper != null) {
                    asyncDbWriter.submit(() -> clickSessionMapper.insert(entity));
                }
            }
        } else if (clickSessionMapper != null) {
            // 本地无 Kafka 模式：使用有界线程池异步落库
            asyncDbWriter.submit(() -> {
                try {
                    clickSessionMapper.insert(entity);
                } catch (Exception ex) {
                    log.warn("Async DB persistence failed for click {}: {}", clickId, ex.getMessage());
                }
            });
        }

        // 6. 落地页链接宏变量替换
        String redirectUrl = buildRedirectUrl(
                offer.landingPageUrl(), clickId, offer.id(), affiliateId,
                sub1, sub2, sub3, sub4, sub5, ip, country, deviceType
        );

        metrics.recordClickSuccess(System.nanoTime() - startNanos);
        return new ClickTrackingResult(clickId, redirectUrl, session);
    }

    /**
     * 根据 click_id 取回点击会话 (本地 -> Redis 分布式 -> PostgreSQL 兜底)
     */
    public ClickSession findSession(String clickId) {
        if (clickId == null) return null;

        // 1. 本地内存命中 (微秒级)
        ClickSession local = sessionStore.get(clickId);
        if (local != null) return local;

        // 2. Redis 分布式会话读取 (支持多节点 Pod 共享)
        if (redisTemplate != null) {
            try {
                String cached = redisTemplate.opsForValue().get("aff:click:sess:" + clickId);
                if (cached != null && !cached.isBlank()) {
                    String[] parts = cached.split("\\|\\|", -1);
                    if (parts.length >= 15) {
                        ClickSession session = new ClickSession(
                                parts[0],
                                parts[1],
                                parts[2],
                                parts[3],
                                parts[4],
                                parts[5],
                                parts[6],
                                parts[7],
                                parts[8],
                                parts[9],
                                parts[10],
                                parts[11],
                                Integer.parseInt(parts[12]),
                                Instant.ofEpochMilli(Long.parseLong(parts[13])),
                                Instant.ofEpochMilli(Long.parseLong(parts[14]))
                        );
                        sessionStore.put(clickId, session);
                        return session;
                    }
                }
            } catch (Exception ex) {
                log.warn("Redis read failed for click session {}: {}", clickId, ex.getMessage());
            }
        }

        // 3. PostgreSQL 回源兜底查询
        if (clickSessionMapper != null) {
            try {
                ClickSessionEntity entity = clickSessionMapper.selectById(clickId);
                if (entity != null) {
                    ClickSession session = new ClickSession(
                            entity.getClickId(),
                            entity.getTenantId(),
                            entity.getOfferId(),
                            entity.getAffiliateId(),
                            entity.getSub1(),
                            entity.getSub2(),
                            entity.getSub3(),
                            entity.getSub4(),
                            entity.getSub5(),
                            entity.getIp(),
                            entity.getUserAgent(),
                            entity.getCountry(),
                            entity.getDeviceType() != null ? entity.getDeviceType() : 0,
                            entity.getCreatedAt(),
                            entity.getExpiresAt()
                    );
                    sessionStore.put(clickId, session);
                    return session;
                }
            } catch (Exception ex) {
                log.warn("DB read failed for click session {}: {}", clickId, ex.getMessage());
            }
        }

        return null;
    }

    /**
     * 替换落地页 URL 中的宏变量（标准参数）
     */
    public String buildRedirectUrl(String template, String clickId, String s1, String s2, String s3, String s4, String s5) {
        return buildRedirectUrl(template, clickId, null, null, s1, s2, s3, s4, s5, null, null, 0);
    }

    /**
     * 替换落地页 URL 中的全量宏变量 (支持 click_id, offer_id, aff_id, sub1~sub5, ip, country, device_type)
     */
    public String buildRedirectUrl(String template, String clickId, String offerId, String affId,
                                   String s1, String s2, String s3, String s4, String s5,
                                   String ip, String country, int deviceType) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String url = template;
        url = url.replace("{click_id}", clickId != null ? clickId : "");
        url = url.replace("{offer_id}", offerId != null ? offerId : "");
        url = url.replace("{aff_id}", affId != null ? affId : "");
        url = url.replace("{sub1}", s1 != null ? s1 : "");
        url = url.replace("{sub2}", s2 != null ? s2 : "");
        url = url.replace("{sub3}", s3 != null ? s3 : "");
        url = url.replace("{sub4}", s4 != null ? s4 : "");
        url = url.replace("{sub5}", s5 != null ? s5 : "");
        url = url.replace("{ip}", ip != null ? ip : "");
        url = url.replace("{country}", country != null ? country : "");
        url = url.replace("{device_type}", String.valueOf(deviceType));
        return url;
    }

    public record ClickTrackingResult(String clickId, String redirectUrl, ClickSession session) {
    }
}
