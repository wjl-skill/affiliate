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
    private static final String REDIS_POOL_PREFIX = "aff:prob:pool:";

    // 存储近 24 小时内的点击候选特征存根: key = offerId + ":" + ipSubnet
    private final ConcurrentMap<String, List<FingerprintCandidate>> candidatePool = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public ProbabilisticAttributionEngine() {}

    public ProbabilisticAttributionEngine(org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setRedisTemplate(org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册点击指纹存根（同时写入本地高速池与可选 Redis 分布式池，支持多节点 Tracker/Postback 集群共享）
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

        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_POOL_PREFIX + poolKey;
                String serialized = serializeCandidate(candidate);
                redisTemplate.opsForList().rightPush(redisKey, serialized);
                redisTemplate.expire(redisKey, FINGERPRINT_TTL);
            } catch (Exception ignored) {
                // Redis 异常时平滑降级为本地内存池
            }
        }
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
        List<FingerprintCandidate> candidates = loadMergedCandidates(poolKey);
        if (candidates.isEmpty()) {
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

        return Math.round(Math.min(score, 1.0) * 10000.0) / 10000.0;
    }

    private List<FingerprintCandidate> loadMergedCandidates(String poolKey) {
        Map<String, FingerprintCandidate> merged = new LinkedHashMap<>();
        List<FingerprintCandidate> local = candidatePool.get(poolKey);
        if (local != null) {
            synchronized (local) {
                cleanExpired(local);
                for (FingerprintCandidate c : local) {
                    merged.put(c.clickId(), c);
                }
            }
        }
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_POOL_PREFIX + poolKey;
                List<String> rawList = redisTemplate.opsForList().range(redisKey, 0, -1);
                if (rawList != null) {
                    Instant cutoff = Instant.now().minus(FINGERPRINT_TTL);
                    for (String raw : rawList) {
                        FingerprintCandidate parsed = deserializeCandidate(raw);
                        if (parsed != null && !parsed.createdAt().isBefore(cutoff)) {
                            merged.putIfAbsent(parsed.clickId(), parsed);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(merged.values());
    }

    private void cleanExpired(List<FingerprintCandidate> list) {
        Instant cutoff = Instant.now().minus(FINGERPRINT_TTL);
        list.removeIf(c -> c.createdAt().isBefore(cutoff));
    }

    /**
     * 提取客户端 IP 子网前缀：
     * - IPv4 提取 /24 C 段子网（如 203.0.113.45 -> 203.0.113）；
     * - IPv6 提取标准 /48 运营商路由前缀（前 6 字节 48 bits，兼容 :: 压缩格式）。
     */
    public String extractSubnet(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";
        String clean = ip.trim();
        if (clean.contains(":")) {
            try {
                byte[] addr = java.net.InetAddress.getByName(clean).getAddress();
                if (addr.length == 16) {
                    return String.format("ipv6_48:%02x%02x:%02x%02x:%02x%02x",
                            addr[0] & 0xFF, addr[1] & 0xFF,
                            addr[2] & 0xFF, addr[3] & 0xFF,
                            addr[4] & 0xFF, addr[5] & 0xFF);
                }
            } catch (Exception ignored) {
            }
            String[] parts = clean.split(":");
            if (parts.length >= 3) {
                return ("ipv6_48:" + parts[0] + ":" + parts[1] + ":" + parts[2]).toLowerCase(Locale.ROOT);
            }
            return clean.toLowerCase(Locale.ROOT);
        }
        int lastDot = clean.lastIndexOf('.');
        return lastDot > 0 ? clean.substring(0, lastDot) : clean;
    }

    private static String serializeCandidate(FingerprintCandidate c) {
        java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
        return String.join("|",
                enc.encodeToString(nullToEmpty(c.clickId()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.tenantId()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.offerId()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.affiliateId()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.sub1()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.sub2()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.sub3()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.sub4()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.sub5()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.ip()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.userAgent()).getBytes(StandardCharsets.UTF_8)),
                enc.encodeToString(nullToEmpty(c.country()).getBytes(StandardCharsets.UTF_8)),
                String.valueOf(c.deviceType()),
                enc.encodeToString(nullToEmpty(c.uaHash()).getBytes(StandardCharsets.UTF_8)),
                String.valueOf(c.createdAt().toEpochMilli())
        );
    }

    private static FingerprintCandidate deserializeCandidate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 15) return null;
        java.util.Base64.Decoder dec = java.util.Base64.getUrlDecoder();
        return new FingerprintCandidate(
                emptyToNull(new String(dec.decode(parts[0]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[1]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[2]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[3]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[4]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[5]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[6]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[7]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[8]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[9]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[10]), StandardCharsets.UTF_8)),
                emptyToNull(new String(dec.decode(parts[11]), StandardCharsets.UTF_8)),
                Integer.parseInt(parts[12]),
                emptyToNull(new String(dec.decode(parts[13]), StandardCharsets.UTF_8)),
                Instant.ofEpochMilli(Long.parseLong(parts[14]))
        );
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
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
