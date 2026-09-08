package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.PerformanceReportCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 性能报表缓存数据访问层
 */
@Repository
public interface PerformanceReportCacheRepository extends JpaRepository<PerformanceReportCacheEntity, String> {

    /**
     * 通过报表哈希查找缓存
     */
    Optional<PerformanceReportCacheEntity> findByReportHash(String reportHash);

    /**
     * 查找指定类型的报表缓存
     */
    List<PerformanceReportCacheEntity> findByReportType(String reportType);

    /**
     * 查找指定渠道的报表缓存
     */
    List<PerformanceReportCacheEntity> findByAffiliateId(String affiliateId);

    /**
     * 查找指定Offer的报表缓存
     */
    List<PerformanceReportCacheEntity> findByOfferId(String offerId);

    /**
     * 查找指定日期范围的报表缓存
     */
    @Query("SELECT r FROM PerformanceReportCacheEntity r WHERE " +
           "r.startDate >= :startDate AND r.endDate <= :endDate")
    List<PerformanceReportCacheEntity> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 删除过期的报表缓存
     */
    @Modifying
    @Query("DELETE FROM PerformanceReportCacheEntity r WHERE r.expiresAt < :now")
    int deleteExpiredReports(@Param("now") Instant now);

    /**
     * 删除指定渠道的所有报表缓存
     */
    @Modifying
    void deleteByAffiliateId(String affiliateId);

    /**
     * 删除指定Offer的所有报表缓存
     */
    @Modifying
    void deleteByOfferId(String offerId);

    /**
     * 统计缓存报表数量
     */
    long countByReportType(String reportType);

    /**
     * 检查报表哈希是否存在且未过期
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM PerformanceReportCacheEntity r WHERE r.reportHash = :hash " +
           "AND r.expiresAt > :now")
    boolean existsValidReport(@Param("hash") String hash, @Param("now") Instant now);
}
