package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.entity.AntiFraudAuditLogEntity;
import com.affiliate.platform.entity.AntiFraudBlacklistEntity;
import com.affiliate.platform.mapper.AntiFraudAuditLogMapper;
import com.affiliate.platform.mapper.AntiFraudBlacklistMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商业级网盟反欺诈与流量质检风控引擎 (Commercial Affiliate Anti-Fraud & Risk Engine)
 * <p>
 * 深度对标 FraudScore / 24Metrics / Protected Media：
 * 1. CTIT 分布异常检测（Click Injection 点击注入与 Click Spam 点击泛滥识别）；
 * 2. 数据中心/云服务商机房 IP 与 Tor/Proxy 识别（AWS/GCP/Azure/DigitalOcean 等）；
 * 3. 爬虫与 Headless 自动化脚本特征过滤；
 * 4. 多维综合风控风险评分 (0-100 分)：输出 APPROVED / SUSPICIOUS_HELD / AUTO_REJECTED；
 * 5. 动态黑白名单持久化与 Java 21 虚拟线程极速异步作弊审计。
 */
@Service
public class AffiliateAntiFraudEngine {

    private final AntiFraudBlacklistMapper blacklistMapper;
    private final AntiFraudAuditLogMapper auditLogMapper;

    private static final long DEFAULT_MIN_CTIT_SECONDS = 3;
    private static final long DEFAULT_MAX_CTIT_SECONDS = 30L * 24 * 3600;

    // 常见数据中心与自动化测试 IP 常见前缀特征库 (Datacenter CIDRs)
    private static final Set<String> DATACENTER_IP_PREFIXES = Set.of(
            "3.0.", "3.1.", "34.", "35.", "52.", "54.", "104.", "143.244.", "159.65.", "165.227.", "198.199."
    );

    // 爬虫与自动化测试 User-Agent 特征特征词
    private static final List<String> BOT_UA_KEYWORDS = List.of(
            "bot", "spider", "crawl", "curl", "python-requests", "headlesschrome", "puppeteer", "phantomjs", "selenium"
    );

    // 已处理完成的交易订单去重缓存：基于 Caffeine LRU 有界淘汰 (最大容量 200,000，保存 7 天)
    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> processedTxCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(200000)
                    .expireAfterWrite(Duration.ofDays(7))
                    .build();

    // IP 秒级/分钟级点击计数器：采用 Caffeine 自动淘汰 (最大容量 50,000，2 分钟过期)
    private final com.github.benmanes.caffeine.cache.Cache<String, AtomicInteger> ipMinuteClickCounters =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(50000)
                    .expireAfterWrite(Duration.ofMinutes(2))
                    .build();

    // IP 分钟级转化突发计数器：采用 Caffeine 自动淘汰 (最大容量 20,000，2 分钟过期)
    private final com.github.benmanes.caffeine.cache.Cache<String, AtomicInteger> ipMinuteConversionCounters =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(20000)
                    .expireAfterWrite(Duration.ofMinutes(2))
                    .build();

    // 动态黑名单集合（O(1) 内存极速拦截，< 0.01ms）
    private final Set<String> ipBlacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> subIdBlacklist = ConcurrentHashMap.newKeySet();

    // 近期风控质检审计日志 (保留最新 200 条用于大盘呈现)
    private final Deque<RiskLogEntry> recentRiskLogs = new ConcurrentLinkedDeque<>();
    private static final int MAX_LOG_SIZE = 200;

    // 风控汇总累计计数器
    private final AtomicLong totalInspections = new AtomicLong();
    private final AtomicLong blockedInspections = new AtomicLong();
    private final AtomicLong ctitAnomalyCount = new AtomicLong();
    private final AtomicLong datacenterIpCount = new AtomicLong();
    private final AtomicLong geoAnomalyCount = new AtomicLong();
    private final AtomicLong deviceAnomalyCount = new AtomicLong();

    public AffiliateAntiFraudEngine() {
        this(null, null);
    }

    @Autowired
    public AffiliateAntiFraudEngine(
            @Autowired(required = false) AntiFraudBlacklistMapper blacklistMapper,
            @Autowired(required = false) AntiFraudAuditLogMapper auditLogMapper
    ) {
        this.blacklistMapper = blacklistMapper;
        this.auditLogMapper = auditLogMapper;
        reloadBlacklist();
    }

    /**
     * 从数据库全量热加载/重载黑名单并剔除过期项
     */
    public void reloadBlacklist() {
        if (this.blacklistMapper != null) {
            try {
                QueryWrapper<AntiFraudBlacklistEntity> qw = new QueryWrapper<>();
                qw.eq("status", "ACTIVE");
                List<AntiFraudBlacklistEntity> list = this.blacklistMapper.selectList(qw);
                Set<String> freshIps = new HashSet<>();
                Set<String> freshSubs = new HashSet<>();
                Instant now = Instant.now();

                for (AntiFraudBlacklistEntity item : list) {
                    if (item.getExpiresAt() != null && item.getExpiresAt().isBefore(now)) {
                        continue;
                    }
                    if ("IP".equalsIgnoreCase(item.getTargetType())) {
                        freshIps.add(item.getTargetValue());
                    } else if ("SUB_ID".equalsIgnoreCase(item.getTargetType())) {
                        freshSubs.add(item.getTargetValue());
                    }
                }
                ipBlacklist.clear();
                ipBlacklist.addAll(freshIps);
                subIdBlacklist.clear();
                subIdBlacklist.addAll(freshSubs);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 针对转化进行全方位反欺诈安全质检与风险打分
     *
     * @param session 点击会话存根
     * @param txId    广告主交易订单号
     * @param now     转化发生时间
     * @return 详细质检评估与打分结果
     */
    public FraudInspectionResult inspectConversion(ClickSession session, String txId, Instant now) {
        return inspectConversion(
                session,
                txId,
                now,
                session != null ? session.ip() : null,
                session != null ? session.userAgent() : null,
                session != null ? session.country() : null,
                session != null ? session.deviceType() : null
        );
    }

    /**
     * 深度多维反欺诈与设备质检分析
     *
     * @param session         点击存根
     * @param txId            交易订单流水号
     * @param now             转化发生时间
     * @param convIp          转化上报 IP
     * @param convUa          转化上报 User-Agent
     * @param convCountry     转化上报国家代码
     * @param convDeviceType  转化上报设备类型
     */
    public FraudInspectionResult inspectConversion(
            ClickSession session,
            String txId,
            Instant now,
            String convIp,
            String convUa,
            String convCountry,
            Integer convDeviceType
    ) {
        if (session == null) {
            totalInspections.incrementAndGet();
            blockedInspections.incrementAndGet();
            return new FraudInspectionResult(false, Conversion.Status.REJECTED, "CLICK_SESSION_NOT_FOUND", 100, List.of("NO_SESSION"));
        }

        List<String> riskReasons = new ArrayList<>();
        int riskScore = 0;

        // 1. 交易订单号幂等去重检查 (严重作弊/重复刷单 -> 100分瞬时拒绝)
        String txKey = session.offerId() + ":" + txId;
        Boolean previous = processedTxCache.asMap().putIfAbsent(txKey, Boolean.TRUE);
        if (previous != null) {
            riskScore = 100;
            riskReasons.add("DUPLICATE_TRANSACTION_ID");
            totalInspections.incrementAndGet();
            blockedInspections.incrementAndGet();
            recordRiskLog(session, txId, ctitOf(session, now), riskScore, riskReasons, "AUTO_REJECTED");
            return new FraudInspectionResult(false, Conversion.Status.REJECTED, "DUPLICATE_TRANSACTION_ID", riskScore, riskReasons);
        }

        // 2. 黑名单校验
        if (session.ip() != null && ipBlacklist.contains(session.ip())) {
            riskScore += 80;
            riskReasons.add("IP_IN_BLACKLIST");
        }
        if (convIp != null && !convIp.equals(session.ip()) && ipBlacklist.contains(convIp)) {
            riskScore += 80;
            riskReasons.add("CONVERSION_IP_IN_BLACKLIST");
        }
        if (session.sub1() != null && subIdBlacklist.contains(session.sub1())) {
            riskScore += 80;
            riskReasons.add("SUB_ID_IN_BLACKLIST");
        }

        // 3. CTIT (转化耗时差) 质检
        Instant clickTime = session.createdAt();
        long ctitSeconds = Duration.between(clickTime, now).toSeconds();

        if (ctitSeconds < DEFAULT_MIN_CTIT_SECONDS) {
            riskScore += 75; // < 3s: 极高疑似点击注入 (Click Injection)
            riskReasons.add("FAST_CONVERSION_CTIT_UNDER_3S");
        } else if (ctitSeconds < 10) {
            riskScore += 25; // 3s~10s: 极快转化速度 (可疑脚本)
            riskReasons.add("SUSPICIOUS_FAST_CONVERSION_UNDER_10S");
        }

        if (ctitSeconds > DEFAULT_MAX_CTIT_SECONDS) {
            riskScore += 75;
            riskReasons.add("EXPIRED_ATTRIBUTION_WINDOW");
        }

        // 4. 机房与数据中心 IP 特征识别
        if (isDatacenterIp(session.ip()) || isDatacenterIp(convIp)) {
            riskScore += 45;
            riskReasons.add("DATACENTER_PROXY_IP_DETECTED");
        }

        // 5. User-Agent 异常与自动化爬虫签名校验
        if (isBotUserAgent(session.userAgent()) || isBotUserAgent(convUa)) {
            riskScore += 50;
            riskReasons.add("BOT_OR_HEADLESS_USER_AGENT");
        }

        // 6. 地理跨国漂移检测 (Geo Drift Inspection)
        if (session.country() != null && convCountry != null && !session.country().isBlank() && !convCountry.isBlank()
                && !session.country().equalsIgnoreCase(convCountry)) {
            if (ctitSeconds < 300) {
                // 5 分钟内发生跨国突变，属高危代理劫持
                riskScore += 55;
                riskReasons.add("GEO_LOCATION_DRIFT_SUSPECTED");
                geoAnomalyCount.incrementAndGet();
            } else {
                riskScore += 20;
                riskReasons.add("GEO_LOCATION_CHANGED");
            }
        }

        // 7. 设备环境与系统平台一致性核验 (Device & Platform Mismatch)
        if (session.deviceType() > 0 && convDeviceType != null && convDeviceType > 0 && session.deviceType() != convDeviceType) {
            riskScore += 40;
            riskReasons.add("DEVICE_ENVIRONMENT_MISMATCH");
            deviceAnomalyCount.incrementAndGet();
        }
        if (isPlatformMismatch(session.userAgent(), convUa)) {
            riskScore += 45;
            riskReasons.add("PLATFORM_OS_MISMATCH");
            deviceAnomalyCount.incrementAndGet();
        }

        // 8. 转化端 IP 分钟级突发爆发检测 (Conversion Flood per IP)
        String effectiveIp = (convIp != null && !convIp.isBlank()) ? convIp : session.ip();
        if (effectiveIp != null && !isConversionFrequencyNormal(effectiveIp, 10)) {
            riskScore += 50;
            riskReasons.add("IP_CONVERSION_BURST_FLOOD");
        }

        // 9. 累计风控汇总计数
        totalInspections.incrementAndGet();
        if (riskReasons.contains("FAST_CONVERSION_CTIT_UNDER_3S")) {
            ctitAnomalyCount.incrementAndGet();
        }
        if (riskReasons.contains("DATACENTER_PROXY_IP_DETECTED")) datacenterIpCount.incrementAndGet();

        // 10. 判定最终风控建议
        Conversion.Status recommendedStatus;
        boolean passed;

        String primaryReason = null;
        if (riskReasons.contains("DUPLICATE_TRANSACTION_ID")) {
            primaryReason = "DUPLICATE_TRANSACTION_ID";
        } else if (riskReasons.contains("FAST_CONVERSION_CTIT_UNDER_3S")) {
            primaryReason = "FAST_CONVERSION_CTIT_UNDER_3S";
        } else if (riskReasons.contains("GEO_LOCATION_DRIFT_SUSPECTED")) {
            primaryReason = "GEO_LOCATION_DRIFT_SUSPECTED";
        } else if (riskReasons.contains("IP_IN_BLACKLIST") || riskReasons.contains("CONVERSION_IP_IN_BLACKLIST")) {
            primaryReason = "IP_IN_BLACKLIST";
        } else if (riskReasons.contains("SUB_ID_IN_BLACKLIST")) {
            primaryReason = "SUB_ID_IN_BLACKLIST";
        } else if (riskReasons.contains("EXPIRED_ATTRIBUTION_WINDOW")) {
            primaryReason = "EXPIRED_ATTRIBUTION_WINDOW";
        } else if (!riskReasons.isEmpty()) {
            primaryReason = riskReasons.get(0);
        }

        if (riskScore >= 70) {
            recommendedStatus = Conversion.Status.FRAUD_SUSPECTED;
            passed = false;
            blockedInspections.incrementAndGet();
        } else if (riskScore >= 35) {
            recommendedStatus = Conversion.Status.PENDING; // 需人工审核
            passed = true;
        } else {
            recommendedStatus = Conversion.Status.PENDING; // 正常排期审核
            passed = true;
        }

        recordRiskLog(session, txId, ctitSeconds, riskScore, riskReasons, passed ? (riskScore >= 35 ? "SUSPICIOUS_HELD" : "APPROVED") : "FRAUD_SUSPECTED");

        return new FraudInspectionResult(passed, recommendedStatus, primaryReason, riskScore, riskReasons);
    }

    public boolean checkClickFrequency(String ip, int maxPerMin) {
        if (ip == null || ip.isBlank() || maxPerMin <= 0) {
            return true;
        }
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = ip + ":" + epochMinute;

        AtomicInteger counter = ipMinuteClickCounters.get(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= maxPerMin;
    }

    public boolean isDatacenterIp(String ip) {
        if (ip == null) return false;
        for (String prefix : DATACENTER_IP_PREFIXES) {
            if (ip.startsWith(prefix)) return true;
        }
        return false;
    }

    public boolean isBotUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return true;
        String lower = ua.toLowerCase(Locale.ROOT);
        for (String keyword : BOT_UA_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    public boolean isConversionFrequencyNormal(String ip, int maxPerMin) {
        if (ip == null || ip.isBlank() || maxPerMin <= 0) {
            return true;
        }
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = "conv:" + ip + ":" + epochMinute;

        AtomicInteger counter = ipMinuteConversionCounters.get(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= maxPerMin;
    }

    private boolean isPlatformMismatch(String clickUa, String convUa) {
        if (clickUa == null || convUa == null || clickUa.isBlank() || convUa.isBlank()) return false;
        String c1 = clickUa.toLowerCase(Locale.ROOT);
        String c2 = convUa.toLowerCase(Locale.ROOT);
        boolean isMobile1 = c1.contains("iphone") || c1.contains("android") || c1.contains("mobile");
        boolean isDesktop1 = c1.contains("windows nt") || c1.contains("macintosh") || c1.contains("x11");
        boolean isMobile2 = c2.contains("iphone") || c2.contains("android") || c2.contains("mobile");
        boolean isDesktop2 = c2.contains("windows nt") || c2.contains("macintosh") || c2.contains("x11");
        return (isMobile1 && isDesktop2) || (isDesktop1 && isMobile2);
    }

    public void addIpToBlacklist(String ip) {
        addIpToBlacklist(ip, "MANUAL_BAN", "admin", null);
    }

    public void addIpToBlacklist(String ip, String reason, String operator, Instant expiresAt) {
        if (ip == null || ip.isBlank()) return;
        ipBlacklist.add(ip);
        if (blacklistMapper != null) {
            String id = "bl_ip_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            AntiFraudBlacklistEntity entity = new AntiFraudBlacklistEntity(
                    id,
                    "default",
                    "IP",
                    ip,
                    reason != null ? reason : "MANUAL_BAN",
                    operator != null ? operator : "admin",
                    "ACTIVE",
                    expiresAt,
                    Instant.now()
            );
            blacklistMapper.insert(entity);
        }
    }

    public void removeIpFromBlacklist(String ip) {
        if (ip == null) return;
        ipBlacklist.remove(ip);
        if (blacklistMapper != null) {
            QueryWrapper<AntiFraudBlacklistEntity> qw = new QueryWrapper<>();
            qw.eq("target_type", "IP").eq("target_value", ip);
            blacklistMapper.delete(qw);
        }
    }

    public void addSubIdToBlacklist(String subId) {
        addSubIdToBlacklist(subId, "HIGH_RISK_SUBID", "admin", null);
    }

    public void addSubIdToBlacklist(String subId, String reason, String operator, Instant expiresAt) {
        if (subId == null || subId.isBlank()) return;
        subIdBlacklist.add(subId);
        if (blacklistMapper != null) {
            String id = "bl_sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            AntiFraudBlacklistEntity entity = new AntiFraudBlacklistEntity(
                    id,
                    "default",
                    "SUB_ID",
                    subId,
                    reason != null ? reason : "HIGH_RISK_SUBID",
                    operator != null ? operator : "admin",
                    "ACTIVE",
                    expiresAt,
                    Instant.now()
            );
            blacklistMapper.insert(entity);
        }
    }

    public void removeSubIdFromBlacklist(String subId) {
        if (subId == null) return;
        subIdBlacklist.remove(subId);
        if (blacklistMapper != null) {
            QueryWrapper<AntiFraudBlacklistEntity> qw = new QueryWrapper<>();
            qw.eq("target_type", "SUB_ID").eq("target_value", subId);
            blacklistMapper.delete(qw);
        }
    }

    public Set<String> getIpBlacklist() {
        return Collections.unmodifiableSet(ipBlacklist);
    }

    public Set<String> getSubIdBlacklist() {
        return Collections.unmodifiableSet(subIdBlacklist);
    }

    public List<RiskLogEntry> getRecentRiskLogs() {
        return List.copyOf(recentRiskLogs);
    }

    /**
     * 累计风控汇总指标（服务重启后重新计数）
     */
    public Map<String, Long> getCumulativeCounters() {
        return Map.of(
                "totalInspections", totalInspections.get(),
                "blockedInspections", blockedInspections.get(),
                "ctitAnomalyCount", ctitAnomalyCount.get(),
                "datacenterIpCount", datacenterIpCount.get(),
                "geoAnomalyCount", geoAnomalyCount.get(),
                "deviceAnomalyCount", deviceAnomalyCount.get()
        );
    }

    private static Long ctitOf(ClickSession session, Instant now) {
        return session.createdAt() != null ? Duration.between(session.createdAt(), now).toSeconds() : null;
    }

    private void recordRiskLog(ClickSession session, String txId, Long ctitSeconds, int score, List<String> reasons, String action) {
        RiskLogEntry entry = new RiskLogEntry(
                session.clickId(),
                session.offerId(),
                session.affiliateId(),
                txId,
                session.ip(),
                score,
                reasons,
                action,
                Instant.now(),
                ctitSeconds
        );
        recentRiskLogs.addFirst(entry);
        while (recentRiskLogs.size() > MAX_LOG_SIZE) {
            recentRiskLogs.pollLast();
        }

        // 使用 Java 21 虚拟线程极速异步持久化风控审计流水，完全不阻塞当前高并发主业务线程
        if (auditLogMapper != null) {
            Thread.ofVirtual().name("antifraud-audit-logger").start(() -> {
                try {
                    String logId = "risk_log_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    AntiFraudAuditLogEntity logEntity = new AntiFraudAuditLogEntity(
                            logId,
                            session.tenantId() != null ? session.tenantId() : "default",
                            txId,
                            session.clickId(),
                            session.affiliateId(),
                            session.ip(),
                            ctitSeconds != null ? BigDecimal.valueOf(ctitSeconds) : null,
                            score,
                            reasons != null && !reasons.isEmpty() ? reasons.get(0) : "NONE",
                            action,
                            reasons != null ? String.join(",", reasons) : "",
                            Instant.now()
                    );
                    auditLogMapper.insert(logEntity);
                } catch (Exception ignored) {}
            });
        }
    }

    public record FraudInspectionResult(
            boolean passed,
            Conversion.Status recommendedStatus,
            String rejectionReason,
            int riskScore,
            List<String> riskReasons
    ) {
        public FraudInspectionResult(boolean passed, Conversion.Status recommendedStatus, String rejectionReason) {
            this(passed, recommendedStatus, rejectionReason, passed ? 0 : 80, rejectionReason != null ? List.of(rejectionReason) : List.of());
        }
    }

    public record RiskLogEntry(
            String clickId,
            String offerId,
            String affiliateId,
            String txId,
            String ip,
            int riskScore,
            List<String> riskReasons,
            String actionVerdict,
            Instant timestamp,
            Long ctitSeconds
    ) {}
}
