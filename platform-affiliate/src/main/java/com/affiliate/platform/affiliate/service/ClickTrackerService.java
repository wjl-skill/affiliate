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

    // 内存降级存储容器
    private final ConcurrentMap<String, ClickSession> sessionStore = new ConcurrentHashMap<>();

    public ClickTrackerService() {
        this(null, null, null);
    }

    public ClickTrackerService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, null, null);
    }

    @Autowired
    public ClickTrackerService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ClickSessionMapper clickSessionMapper,
            @Autowired(required = false) ProbabilisticAttributionEngine probabilisticEngine
    ) {
        this.redisTemplate = redisTemplate;
        this.clickSessionMapper = clickSessionMapper;
        this.probabilisticEngine = probabilisticEngine;
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

        // 3. 沉淀会话存根至本地高可用内存容器与指纹池（纳秒级即时对齐后续高并发微秒级 Postback）
        sessionStore.put(clickId, session);
        if (probabilisticEngine != null) {
            try {
                probabilisticEngine.registerClickFingerprint(session);
            } catch (Exception ignored) {}
        }

        // 使用 Java 21 虚拟线程将数据库和 Redis I/O 异步化，主线程极速完成并返回 302 重定向
        Thread.ofVirtual().name("click-async-writer-" + clickId).start(() -> {
            if (clickSessionMapper != null) {
                try {
                    ClickSessionEntity entity = new ClickSessionEntity(
                            clickId,
                            offer.tenantId(),
                            offer.id(),
                            affiliateId,
                            sub1, sub2, sub3, sub4, sub5,
                            ip, userAgent, country, deviceType,
                            now, expiresAt
                    );
                    clickSessionMapper.insert(entity);
                } catch (Exception ignored) {}
            }

            if (redisTemplate != null) {
                try {
                    // 缓存 30 天
                    redisTemplate.opsForValue().set("aff:click:" + clickId, offer.id() + ":" + affiliateId, Duration.ofDays(30));
                } catch (Exception ignored) {}
            }
        });

        // 4. 落地页链接宏变量替换
        String redirectUrl = buildRedirectUrl(offer.landingPageUrl(), clickId, sub1, sub2, sub3, sub4, sub5);

        return new ClickTrackingResult(clickId, redirectUrl, session);
    }

    /**
     * 根据 click_id 取回点击会话
     */
    public ClickSession findSession(String clickId) {
        if (clickId == null) return null;

        if (clickSessionMapper != null) {
            try {
                ClickSessionEntity entity = clickSessionMapper.selectById(clickId);
                if (entity != null) {
                    return new ClickSession(
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
                }
            } catch (Exception ignored) {}
        }

        return sessionStore.get(clickId);
    }

    /**
     * 替换落地页 URL 中的宏变量
     */
    public String buildRedirectUrl(String template, String clickId, String s1, String s2, String s3, String s4, String s5) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String url = template;
        url = url.replace("{click_id}", clickId != null ? clickId : "");
        url = url.replace("{sub1}", s1 != null ? s1 : "");
        url = url.replace("{sub2}", s2 != null ? s2 : "");
        url = url.replace("{sub3}", s3 != null ? s3 : "");
        url = url.replace("{sub4}", s4 != null ? s4 : "");
        url = url.replace("{sub5}", s5 != null ? s5 : "");
        return url;
    }

    public record ClickTrackingResult(String clickId, String redirectUrl, ClickSession session) {}
}
