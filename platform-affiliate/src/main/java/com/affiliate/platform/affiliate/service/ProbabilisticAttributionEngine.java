package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.ClickSession;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 概率性与设备指纹兜底归因引擎 (Probabilistic & Fingerprint Attribution Engine)
 * <p>
 * 解决 iOS Safari ITP、无 Cookie 模式或广告主 S2S Postback 中 click_id 丢失时的归因难题：
 * 基于访客 IP 段、User-Agent 签名、国家地理及终端环境生成加权指纹，
 * 在可配置的短时窗口（默认 24 小时）内进行模糊匹配置信度评分（Confidence Score）。
 */
@Service
public class ProbabilisticAttributionEngine {

    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.80;
    private static final Duration FINGERPRINT_TTL = Duration.ofHours(24);

    // 存储近 24 小时内的点击候选特征存根: key = offerId + ":" + ipSubnet
    private final ConcurrentMap<String, List<FingerprintCandidate>> candidatePool = new ConcurrentHashMap<>();

    /**
     * 注册点击指纹存根
     */
    public void registerClickFingerprint(ClickSession session) {
        if (session == null || session.ip() == null) return;

        String subnet = extractSubnet(session.ip());
        String poolKey = session.offerId() + ":" + subnet;

        FingerprintCandidate candidate = new FingerprintCandidate(
                session.clickId(),
                session.tenantId(),
                session.offerId(),
                session.affiliateId(),
                session.sub1(), session.sub2(), session.sub3(), session.sub4(), session.sub5(),
                session.ip(),
                session.userAgent(),
                session.country(),
                session.deviceType(),
                hashString(session.userAgent()),
                session.createdAt() != null ? session.createdAt() : Instant.now()
        );

        candidatePool.compute(poolKey, (k, list) -> {
            List<FingerprintCandidate> newList = list == null ? new ArrayList<>() : new ArrayList<>(list);
            cleanExpired(newList);
            newList.add(candidate);
            return newList;
        });
    }

    /**
     * 当 click_id 缺失时，尝试概率性模糊匹配
     *
     * @param offerId    目标 Offer ID
     * @param ip         转化上报客户端 IP
     * @param userAgent  转化上报客户端 User-Agent
     * @param country    转化上报国家
     * @param deviceType 转化上报设备类型
     * @return 匹配判定与置信度打分
     */
    public ProbabilisticMatchResult matchAttribution(
            String offerId,
            String ip,
            String userAgent,
            String country,
            int deviceType
    ) {
        if (offerId == null || ip == null) {
            return ProbabilisticMatchResult.noMatch();
        }

        String subnet = extractSubnet(ip);
        String poolKey = offerId + ":" + subnet;
        List<FingerprintCandidate> candidates = candidatePool.get(poolKey);
        if (candidates == null || candidates.isEmpty()) {
            return ProbabilisticMatchResult.noMatch();
        }

        String uaHash = hashString(userAgent);
        Instant now = Instant.now();

        FingerprintCandidate bestCandidate = null;
        double bestScore = 0.0;

        synchronized (candidates) {
            cleanExpired(candidates);
            for (FingerprintCandidate c : candidates) {
                double score = calculateConfidence(c, ip, uaHash, country, deviceType, now);
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = c;
                }
            }
        }

        if (bestScore >= DEFAULT_CONFIDENCE_THRESHOLD && bestCandidate != null) {
            ClickSession session = new ClickSession(
                    bestCandidate.clickId(),
                    bestCandidate.tenantId(),
                    bestCandidate.offerId(),
                    bestCandidate.affiliateId(),
                    bestCandidate.sub1(), bestCandidate.sub2(), bestCandidate.sub3(), bestCandidate.sub4(), bestCandidate.sub5(),
                    bestCandidate.ip(),
                    bestCandidate.userAgent(),
                    bestCandidate.country(),
                    bestCandidate.deviceType(),
                    bestCandidate.createdAt(),
                    bestCandidate.createdAt().plus(FINGERPRINT_TTL)
            );
            return new ProbabilisticMatchResult(true, bestScore, session);
        }

        return new ProbabilisticMatchResult(false, bestScore, null);
    }

    private double calculateConfidence(FingerprintCandidate c, String ip, String uaHash, String country, int deviceType, Instant now) {
        double score = 0.0;

        // 1. IP 完全匹配权重 (+0.45) 或 C 段子网匹配 (+0.25)
        if (ip.equals(c.ip())) {
            score += 0.45;
        } else if (extractSubnet(ip).equals(extractSubnet(c.ip()))) {
            score += 0.25;
        }

        // 2. User-Agent 哈希指纹完全吻合 (+0.35)
        if (uaHash != null && uaHash.equals(c.uaHash())) {
            score += 0.35;
        }

        // 3. 地理国家代码匹配 (+0.12)
        if (country != null && country.equalsIgnoreCase(c.country())) {
            score += 0.12;
        }

        // 4. 终端设备形态匹配 (+0.08)
        if (deviceType == c.deviceType()) {
            score += 0.08;
        }

        // 5. 时间衰减惩罚（越接近 24h 衰减越大）
        Duration diff = Duration.between(c.createdAt(), now);
        if (diff.toHours() > 12) {
            score *= 0.85; // 超过 12 小时打 85 折
        }

        return Math.min(score, 1.0);
    }

    private void cleanExpired(List<FingerprintCandidate> list) {
        Instant cutoff = Instant.now().minus(FINGERPRINT_TTL);
        list.removeIf(c -> c.createdAt().isBefore(cutoff));
    }

    private String extractSubnet(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : ip;
    }

    private String hashString(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public record ProbabilisticMatchResult(boolean matched, double confidenceScore, ClickSession matchedSession) {
        public static ProbabilisticMatchResult noMatch() {
            return new ProbabilisticMatchResult(false, 0.0, null);
        }
    }

    private record FingerprintCandidate(
            String clickId,
            String tenantId,
            String offerId,
            String affiliateId,
            String sub1, String sub2, String sub3, String sub4, String sub5,
            String ip,
            String userAgent,
            String country,
            int deviceType,
            String uaHash,
            Instant createdAt
    ) {}
}
