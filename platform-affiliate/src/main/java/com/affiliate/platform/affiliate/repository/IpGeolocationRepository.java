package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.IpGeolocationCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * IP 地理位置缓存数据访问层
 */
@Repository
public interface IpGeolocationRepository extends JpaRepository<IpGeolocationCacheEntity, String> {

    /**
     * 根据 IP 地址查找缓存记录
     */
    Optional<IpGeolocationCacheEntity> findByIpAddress(String ipAddress);

    /**
     * 查找所有 VPN/Proxy/Tor IP
     */
    @Query("SELECT g FROM IpGeolocationCacheEntity g WHERE g.isVpn = true OR g.isProxy = true OR g.isTor = true")
    List<IpGeolocationCacheEntity> findSuspiciousIps();

    /**
     * 查找指定国家的所有 IP
     */
    List<IpGeolocationCacheEntity> findByCountryCode(String countryCode);

    /**
     * 查找高风险 IP（风险评分 >= 阈值）
     */
    @Query("SELECT g FROM IpGeolocationCacheEntity g WHERE g.riskScore >= :minScore ORDER BY g.riskScore DESC")
    List<IpGeolocationCacheEntity> findHighRiskIps(@Param("minScore") int minScore);

    /**
     * 查找过期缓存记录（用于清理）
     */
    @Query("SELECT g FROM IpGeolocationCacheEntity g WHERE g.updatedAt < :threshold")
    List<IpGeolocationCacheEntity> findStaleRecords(@Param("threshold") Instant threshold);

    /**
     * 删除过期缓存记录
     */
    void deleteByUpdatedAtBefore(Instant threshold);

    /**
     * 删除过期记录（返回删除数量）
     */
    @Modifying
    @Query("DELETE FROM IpGeolocationCacheEntity g WHERE g.updatedAt < :threshold")
    int deleteStaleRecords(@Param("threshold") Instant threshold);

    /**
     * 统计国家分布
     */
    @Query("SELECT g.countryCode, COUNT(g) FROM IpGeolocationCacheEntity g GROUP BY g.countryCode ORDER BY COUNT(g) DESC")
    List<Object[]> countByCountry();

    /**
     * 获取国家分布（Map格式）
     */
    @Query("SELECT g.countryCode as country, COUNT(g) as count FROM IpGeolocationCacheEntity g GROUP BY g.countryCode")
    List<Object[]> getCountryDistribution();

    /**
     * 统计 ISP 分布
     */
    @Query("SELECT g.isp, COUNT(g) FROM IpGeolocationCacheEntity g WHERE g.isp IS NOT NULL GROUP BY g.isp ORDER BY COUNT(g) DESC")
    List<Object[]> countByIsp();

    /**
     * 检查 IP 是否存在
     */
    boolean existsByIpAddress(String ipAddress);
}
