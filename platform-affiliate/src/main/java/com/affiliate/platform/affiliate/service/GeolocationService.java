package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.IpGeolocationCacheEntity;
import com.affiliate.platform.affiliate.repository.IpGeolocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 地理定位服务（接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. IP 地理位置查询（国家、城市、经纬度）
 * 2. ISP 识别
 * 3. VPN/Proxy/Tor 检测
 * 4. 数据中心 IP 识别
 * 5. 时区转换
 * 6. 地理距离计算
 * 7. 地理围栏（Geofencing）
 * 8. IP 风险评分
 * <p>
 * 对标：MaxMind GeoIP2、IP2Location、IPinfo、SEON Fraud API
 */
@Service
public class GeolocationService {

    private final IpGeolocationRepository geoRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;

    private static final Duration GEO_CACHE_TTL = Duration.ofHours(24); // IP 地理位置缓存 24 小时
    private static final Duration RISK_CACHE_TTL = Duration.ofHours(1); // 风险评分缓存 1 小时
    private static final Duration STALE_THRESHOLD = Duration.ofDays(90); // 90 天未更新的记录视为过期

    // 内存中的 VPN/数据中心 IP 范围（启动时加载）
    private final Set<String> datacenterIpRanges = ConcurrentHashMap.newKeySet();
    private final Map<String, GeofenceRule> geofences = new ConcurrentHashMap<>();

    public GeolocationService(
            IpGeolocationRepository geoRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator
    ) {
        this.geoRepository = geoRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        initializeDatacenterRanges();
    }

    private void initializeDatacenterRanges() {
        // TODO: 从外部数据源加载已知数据中心 IP 范围
        datacenterIpRanges.add("192.0.2.0/24");  // TEST-NET-1
        datacenterIpRanges.add("198.51.100.0/24"); // TEST-NET-2
    }

    /**
     * 查询 IP 地理位置（带缓存）
     */
    public IpGeolocation lookup(String ipAddress) {
        String cacheKey = keyGenerator.ipGeolocation(ipAddress);

        return cacheManager.get(
                cacheKey,
                IpGeolocationCacheEntity.class,
                GEO_CACHE_TTL,
                () -> {
                    // 从数据库查询
                    Optional<IpGeolocationCacheEntity> existing = geoRepository.findByIpAddress(ipAddress);
                    if (existing.isPresent() && !isStale(existing.get())) {
                        return existing.get();
                    }

                    // 调用外部 API 获取新数据
                    IpGeolocation freshData = fetchFromExternalApi(ipAddress);

                    // 保存到数据库
                    IpGeolocationCacheEntity entity = toEntity(freshData);
                    geoRepository.save(entity);

                    return entity;
                }
        ).map(this::toIpGeolocation).orElse(null);
    }

    /**
     * 批量查询
     */
    public Map<String, IpGeolocation> lookupBatch(List<String> ipAddresses) {
        Map<String, IpGeolocation> results = new HashMap<>();

        for (String ip : ipAddresses) {
            try {
                results.put(ip, lookup(ip));
            } catch (Exception e) {
                // 忽略错误，继续处理
            }
        }

        return results;
    }

    /**
     * 检测 VPN/Proxy（带缓存）
     */
    public boolean isVpnOrProxy(String ipAddress) {
        IpGeolocation geo = lookup(ipAddress);
        if (geo == null) {
            return false;
        }

        // 从缓存的地理位置数据中读取
        String cacheKey = keyGenerator.ipGeolocation(ipAddress);
        Optional<IpGeolocationCacheEntity> cached = cacheManager.get(
                cacheKey,
                IpGeolocationCacheEntity.class,
                GEO_CACHE_TTL,
                () -> geoRepository.findByIpAddress(ipAddress).orElse(null)
        );

        return cached.map(entity ->
                Boolean.TRUE.equals(entity.getIsVpn()) || Boolean.TRUE.equals(entity.getIsProxy())
        ).orElse(false);
    }

    /**
     * 检测数据中心 IP（带缓存）
     */
    public boolean isDatacenterIp(String ipAddress) {
        IpGeolocation geo = lookup(ipAddress);
        if (geo == null) {
            return false;
        }

        String cacheKey = keyGenerator.ipGeolocation(ipAddress);
        Optional<IpGeolocationCacheEntity> cached = cacheManager.get(
                cacheKey,
                IpGeolocationCacheEntity.class,
                GEO_CACHE_TTL,
                () -> geoRepository.findByIpAddress(ipAddress).orElse(null)
        );

        return cached.map(entity -> Boolean.TRUE.equals(entity.getIsDatacenter())).orElse(false);
    }

    /**
     * 检测 Tor 出口节点（带缓存）
     */
    public boolean isTorExitNode(String ipAddress) {
        IpGeolocation geo = lookup(ipAddress);
        if (geo == null) {
            return false;
        }

        String cacheKey = keyGenerator.ipGeolocation(ipAddress);
        Optional<IpGeolocationCacheEntity> cached = cacheManager.get(
                cacheKey,
                IpGeolocationCacheEntity.class,
                GEO_CACHE_TTL,
                () -> geoRepository.findByIpAddress(ipAddress).orElse(null)
        );

        return cached.map(entity -> Boolean.TRUE.equals(entity.getIsTor())).orElse(false);
    }

    /**
     * 计算 IP 风险评分（带缓存）
     */
    public IpRiskScore calculateRiskScore(String ipAddress) {
        String cacheKey = keyGenerator.ipRiskScore(ipAddress);

        return cacheManager.get(
                cacheKey,
                IpRiskScore.class,
                RISK_CACHE_TTL,
                () -> performRiskCalculation(ipAddress)
        ).orElseGet(() -> performRiskCalculation(ipAddress));
    }

    private IpRiskScore performRiskCalculation(String ipAddress) {
        int riskScore = 0;
        List<String> reasons = new ArrayList<>();

        // 1. VPN/Proxy 检测 (+40 分)
        if (isVpnOrProxy(ipAddress)) {
            riskScore += 40;
            reasons.add("VPN or Proxy detected");
        }

        // 2. 数据中心 IP (+30 分)
        if (isDatacenterIp(ipAddress)) {
            riskScore += 30;
            reasons.add("Datacenter IP");
        }

        // 3. Tor 出口节点 (+50 分)
        if (isTorExitNode(ipAddress)) {
            riskScore += 50;
            reasons.add("Tor exit node");
        }

        // 4. 地理位置异常
        IpGeolocation geo = lookup(ipAddress);
        if (geo != null && isHighRiskCountry(geo.countryCode())) {
            riskScore += 20;
            reasons.add("High-risk country: " + geo.countryCode());
        }

        // 5. ISP 类型
        if (geo != null && geo.isp() != null && geo.isp().toLowerCase().contains("mobile")) {
            riskScore += 10;
            reasons.add("Mobile ISP");
        }

        RiskLevel riskLevel = getRiskLevel(riskScore);

        return new IpRiskScore(ipAddress, riskScore, riskLevel, reasons);
    }

    /**
     * 计算两个地理位置之间的距离（Haversine 公式）
     */
    public double calculateDistance(
            double lat1, double lon1,
            double lat2, double lon2
    ) {
        final int EARTH_RADIUS_KM = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * 检查 IP 是否在地理围栏内
     */
    public boolean isInGeofence(String ipAddress, String geofenceId) {
        GeofenceRule rule = geofences.get(geofenceId);
        if (rule == null) {
            return false;
        }

        IpGeolocation geo = lookup(ipAddress);
        if (geo == null) {
            return false;
        }

        // 检查国家
        if (rule.allowedCountries() != null && !rule.allowedCountries().isEmpty()) {
            if (!rule.allowedCountries().contains(geo.countryCode())) {
                return false;
            }
        }

        // 检查半径
        if (rule.centerLat() != null && rule.centerLon() != null && rule.radiusKm() != null) {
            double distance = calculateDistance(
                    rule.centerLat(), rule.centerLon(),
                    geo.latitude(), geo.longitude()
            );

            if (distance > rule.radiusKm()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 创建地理围栏规则
     */
    public GeofenceRule createGeofence(
            String name,
            Set<String> allowedCountries,
            Double centerLat,
            Double centerLon,
            Double radiusKm
    ) {
        String geofenceId = UUID.randomUUID().toString();

        GeofenceRule rule = new GeofenceRule(
                geofenceId,
                name,
                allowedCountries,
                centerLat,
                centerLon,
                radiusKm
        );

        geofences.put(geofenceId, rule);

        return rule;
    }

    /**
     * 获取时区
     */
    public ZoneId getTimezone(String ipAddress) {
        IpGeolocation geo = lookup(ipAddress);
        if (geo == null || geo.timezone() == null) {
            return ZoneId.of("UTC");
        }

        try {
            return ZoneId.of(geo.timezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    /**
     * IP 归属分析（检测多个点击是否来自同一物理位置）
     */
    public IpClusterAnalysis analyzeIpCluster(List<String> ipAddresses) {
        Map<String, Integer> countryDistribution = new HashMap<>();
        Map<String, Integer> cityDistribution = new HashMap<>();
        Map<String, Integer> ispDistribution = new HashMap<>();
        int vpnCount = 0;
        int datacenterCount = 0;

        for (String ip : ipAddresses) {
            IpGeolocation geo = lookup(ip);
            if (geo != null) {
                countryDistribution.merge(geo.countryCode(), 1, Integer::sum);
                cityDistribution.merge(geo.city(), 1, Integer::sum);
                ispDistribution.merge(geo.isp(), 1, Integer::sum);
            }

            if (isVpnOrProxy(ip)) vpnCount++;
            if (isDatacenterIp(ip)) datacenterCount++;
        }

        // 分析是否集中在同一位置
        boolean concentrated = countryDistribution.size() <= 2 && cityDistribution.size() <= 3;
        boolean suspicious = vpnCount > ipAddresses.size() * 0.5 || datacenterCount > ipAddresses.size() * 0.3;

        return new IpClusterAnalysis(
                ipAddresses.size(),
                countryDistribution,
                cityDistribution,
                ispDistribution,
                vpnCount,
                datacenterCount,
                concentrated,
                suspicious
        );
    }

    /**
     * 获取高风险IP列表（V2新增方法）
     */
    public List<IpGeolocation> getHighRiskIps(int minScore) {
        List<IpGeolocationCacheEntity> highRiskIps = geoRepository.findHighRiskIps(minScore);

        return highRiskIps.stream()
                .map(this::toIpGeolocation)
                .collect(Collectors.toList());
    }

    /**
     * 获取国家分布统计（V2新增方法）
     */
    public Map<String, Long> getCountryDistribution() {
        List<Object[]> results = geoRepository.getCountryDistribution();
        Map<String, Long> distribution = new HashMap<>();

        for (Object[] row : results) {
            String country = (String) row[0];
            Long count = (Long) row[1];
            distribution.put(country, count);
        }

        return distribution;
    }

    /**
     * 清理过期记录（V2新增方法）
     */
    @Transactional
    public int cleanupStaleRecords() {
        Instant threshold = Instant.now().minus(STALE_THRESHOLD);
        int deleted = geoRepository.deleteStaleRecords(threshold);

        // 失效所有地理位置缓存
        cacheManager.evictByPattern("affiliate:geo:*");

        return deleted;
    }

    // ========== 私有辅助方法 ==========

    private boolean isStale(IpGeolocationCacheEntity entity) {
        Instant threshold = Instant.now().minus(STALE_THRESHOLD);
        return entity.getUpdatedAt() != null && entity.getUpdatedAt().isBefore(threshold);
    }

    private IpGeolocation fetchFromExternalApi(String ipAddress) {
        // TODO: 调用 MaxMind GeoIP2 API
        // 当前使用模拟实现
        return simulateGeoLookup(ipAddress);
    }

    private IpGeolocationCacheEntity toEntity(IpGeolocation geo) {
        return new IpGeolocationCacheEntity(
                UUID.randomUUID().toString(),
                geo.ipAddress(),
                geo.countryCode(),
                geo.countryName(),
                geo.city(),
                geo.region(),
                geo.latitude(),
                geo.longitude(),
                geo.timezone(),
                geo.isp(),
                geo.asn(),
                geo.isVpn(),
                geo.isProxy(),
                geo.isTor(),
                geo.isDatacenter(),
                0, // riskScore 需单独计算
                "LOW",
                Instant.now(),
                null
        );
    }

    private IpGeolocation toIpGeolocation(IpGeolocationCacheEntity entity) {
        return new IpGeolocation(
                entity.getIpAddress(),
                entity.getCountryCode(),
                entity.getCountryName(),
                entity.getCity(),
                entity.getRegion(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getTimezone(),
                entity.getIsp(),
                entity.getAsn(),
                entity.getIsVpn(),
                entity.getIsProxy(),
                entity.getIsTor(),
                entity.getIsDatacenter()
        );
    }

    private IpGeolocation simulateGeoLookup(String ipAddress) {
        // 简化实现：基于 IP 前缀模拟
        if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
            return new IpGeolocation(
                    ipAddress,
                    "ZZ",
                    "Unknown",
                    "Private Network",
                    "Private Network",
                    0.0,
                    0.0,
                    "UTC",
                    "Local ISP",
                    "AS0",
                    false,
                    false,
                    false,
                    false
            );
        }

        // 模拟美国 IP
        return new IpGeolocation(
                ipAddress,
                "US",
                "United States",
                "New York",
                "New York",
                40.7128,
                -74.0060,
                "America/New_York",
                "Example ISP",
                "AS15169",
                false,
                false,
                false,
                false
        );
    }

    private boolean ipInRange(String ip, String cidr) {
        // 简化实现
        // TODO: 实际 CIDR 匹配算法
        return false;
    }

    private boolean isHighRiskCountry(String countryCode) {
        // 根据业务需求配置高风险国家列表
        Set<String> highRiskCountries = Set.of(
                // 示例，实际应从配置读取
        );
        return highRiskCountries.contains(countryCode);
    }

    private RiskLevel getRiskLevel(int score) {
        if (score >= 70) return RiskLevel.CRITICAL;
        if (score >= 50) return RiskLevel.HIGH;
        if (score >= 30) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    // ========== 数据记录 ==========

    public record IpGeolocation(
            String ipAddress,
            String countryCode,
            String countryName,
            String city,
            String region,
            double latitude,
            double longitude,
            String timezone,
            String isp,
            String asn,
            Boolean isVpn,
            Boolean isProxy,
            Boolean isTor,
            Boolean isDatacenter
    ) {}

    public record IpRiskScore(
            String ipAddress,
            int riskScore,
            RiskLevel riskLevel,
            List<String> reasons
    ) {}

    public record GeofenceRule(
            String id,
            String name,
            Set<String> allowedCountries,
            Double centerLat,
            Double centerLon,
            Double radiusKm
    ) {}

    public record IpClusterAnalysis(
            int totalIps,
            Map<String, Integer> countryDistribution,
            Map<String, Integer> cityDistribution,
            Map<String, Integer> ispDistribution,
            int vpnCount,
            int datacenterCount,
            boolean concentrated,
            boolean suspicious
    ) {}

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
